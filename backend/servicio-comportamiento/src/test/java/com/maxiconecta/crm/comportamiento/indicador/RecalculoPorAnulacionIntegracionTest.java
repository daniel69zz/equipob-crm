package com.maxiconecta.crm.comportamiento.indicador;

import com.maxiconecta.crm.comportamiento.compra.ClientePerfiles;
import com.maxiconecta.crm.comportamiento.compra.FrecuenciaCompraRepository;
import com.maxiconecta.crm.comportamiento.ingesta.EstadoEvento;
import com.maxiconecta.crm.comportamiento.ingesta.EventoRecibidoRepository;
import com.maxiconecta.crm.comportamiento.ingesta.IngestaCompras;
import com.maxiconecta.crm.comportamiento.inactividad.DetectorClientesInactivos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static com.maxiconecta.crm.comportamiento.ingesta.LectorEventosTest.ejemplo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-523 · Recálculo de los cuatro indicadores del cliente (ticket promedio, frecuencia, recencia
 * y valor acumulado) y del listado de inactivos cuando una compra se anula o se devuelve en parte
 * (docs/compra/recalculo-por-anulacion.md), con PostgreSQL real.
 * <p>
 * Las compras de ejemplo son V-100234 (cliente CLI-5521, 350.50, 2026-09-27T19:28:10Z) y MP-88120
 * (cliente mp-user-3307, 129.90, 2026-09-27T20:02:12Z); se consultan como dos identificadores del
 * mismo cliente.
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RecalculoPorAnulacionIntegracionTest {

    private static final String ID_ANA = "CLI-5521";
    private static final String ID_CARLOS = "mp-user-3307";
    private static final String RUTA = "/api/comportamiento/clientes/42/indicadores";
    private static final String RUTA_INACTIVOS = "/api/comportamiento/clientes?estado=inactivo";
    private static final Instant REFERENCIA = Instant.parse("2026-09-29T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private ClientePerfiles perfiles;

    @MockBean
    private Clock reloj;

    @Autowired
    private IngestaCompras ingesta;

    @Autowired
    private EventoRecibidoRepository eventos;

    @Autowired
    private FrecuenciaCompraRepository frecuencias;

    @Autowired
    private DetectorClientesInactivos detector;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE comportamiento.cliente_inactivo, comportamiento.frecuencia_compra, "
                + "comportamiento.intento_reproceso, comportamiento.evento_procesado, "
                + "comportamiento.anulacion_item, comportamiento.anulacion, "
                + "comportamiento.compra_item, comportamiento.compra, comportamiento.evento_recibido");
        when(perfiles.buscar(anyString())).thenReturn(Optional.empty());
        when(reloj.instant()).thenReturn(REFERENCIA);
        when(reloj.getZone()).thenReturn(ZoneOffset.UTC);
    }

    // --- Anulación de la única compra: todas las métricas quedan sin datos ---

    @Test
    void anularLaUnicaCompraDejaLosCuatroIndicadoresSinDatos() throws Exception {
        ingesta.recibir(ejemplo("compra-ana.json"));
        mvc.perform(get(RUTA).param("identificador", ID_ANA))
                .andExpect(jsonPath("$.frecuencia").value(1))
                .andExpect(jsonPath("$.ticketPromedio.sinDatos").value(false));

        anularTotal(ejemplo("anulacion-total.json").replace("300.00", "350.50"));

        mvc.perform(get(RUTA).param("identificador", ID_ANA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketPromedio.sinDatos").value(true))
                .andExpect(jsonPath("$.ticketPromedio.valor").doesNotExist())
                .andExpect(jsonPath("$.ticketPromedio.compras").value(0))
                .andExpect(jsonPath("$.frecuencia").value(0))
                .andExpect(jsonPath("$.recencia.sinDatos").value(true))
                .andExpect(jsonPath("$.recencia.ultimaCompra").doesNotExist())
                .andExpect(jsonPath("$.valorAcumulado.sinDatos").value(true))
                .andExpect(jsonPath("$.valorAcumulado.valor").value(0))
                .andExpect(jsonPath("$.valorAcumulado.compras").value(0));
    }

    @Test
    void laCompraAnuladaSigueEnElHistorialConSuAnulacion() {
        ingesta.recibir(ejemplo("compra-ana.json"));

        anularTotal(ejemplo("anulacion-total.json").replace("300.00", "350.50"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM comportamiento.compra", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT estado FROM comportamiento.compra "
                + "WHERE id_compra_origen = 'V-100234'", String.class)).isEqualTo("ANULADA");
        assertThat(jdbc.queryForObject("SELECT monto_revertido FROM comportamiento.compra "
                + "WHERE id_compra_origen = 'V-100234'", BigDecimal.class)).isEqualByComparingTo("350.50");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comportamiento.anulacion "
                + "WHERE id_anulacion_origen = 'V-100234-A1' AND tipo = 'TOTAL'", Long.class)).isEqualTo(1L);
    }

    // --- Anulación de una compra entre varias: el resto sigue contando ---

    @Test
    void anularUnaEntreVariasRecalculaLosIndicadoresConLasQueQuedan() throws Exception {
        ingesta.recibir(ejemplo("compra-ana.json"));
        ingesta.recibir(ejemplo("compra-carlos.json"));
        mvc.perform(get(RUTA).param("identificador", ID_ANA, ID_CARLOS))
                .andExpect(jsonPath("$.frecuencia").value(2))
                .andExpect(jsonPath("$.recencia.ultimaCompra").value("2026-09-27T20:02:12Z"));

        // Se anula la más reciente: la recencia retrocede a la compra anterior.
        anularTotal(ejemplo("anulacion-total-carlos.json"));

        mvc.perform(get(RUTA).param("identificador", ID_ANA, ID_CARLOS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketPromedio.valor").value(350.50))
                .andExpect(jsonPath("$.ticketPromedio.compras").value(1))
                .andExpect(jsonPath("$.frecuencia").value(1))
                .andExpect(jsonPath("$.recencia.ultimaCompra").value("2026-09-27T19:28:10Z"))
                .andExpect(jsonPath("$.recencia.tiempoTranscurrido").value("PT40H31M50S"))
                .andExpect(jsonPath("$.recencia.sinDatos").value(false))
                .andExpect(jsonPath("$.valorAcumulado.valor").value(350.50))
                .andExpect(jsonPath("$.valorAcumulado.compras").value(1));
    }

    @Test
    void laFrecuenciaSoloBajaParaElIdentificadorDeLaCompraAnulada() throws Exception {
        ingesta.recibir(ejemplo("compra-ana.json"));
        ingesta.recibir(ejemplo("compra-carlos.json"));

        anularTotal(ejemplo("anulacion-total-carlos.json"));

        assertThat(frecuencias.findById(ID_ANA)).get().extracting("cantidad").isEqualTo(1L);
        assertThat(frecuencias.findById(ID_CARLOS)).get().extracting("cantidad").isEqualTo(0L);
        mvc.perform(get(RUTA).param("identificador", ID_ANA)).andExpect(jsonPath("$.frecuencia").value(1));
        mvc.perform(get(RUTA).param("identificador", ID_CARLOS)).andExpect(jsonPath("$.frecuencia").value(0));
    }

    // --- Devolución parcial: se excluye lo devuelto, la compra sigue contando ---

    @Test
    void unaDevolucionParcialExcluyeElMontoRevertidoPeroConservaLaCompra() throws Exception {
        ingesta.recibir(ejemplo("compra-ana.json"));
        ingesta.recibir(ejemplo("compra-carlos.json"));

        anularTotal(ejemplo("anulacion-parcial.json"));

        // Ventas conserva 300.00 de 350.50 tras devolver 50.50: (300.00 + 129.90) / 2
        mvc.perform(get(RUTA).param("identificador", ID_ANA, ID_CARLOS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketPromedio.valor").value(214.95))
                .andExpect(jsonPath("$.ticketPromedio.compras").value(2))
                .andExpect(jsonPath("$.frecuencia").value(2))
                .andExpect(jsonPath("$.recencia.ultimaCompra").value("2026-09-27T20:02:12Z"))
                .andExpect(jsonPath("$.valorAcumulado.valor").value(429.90))
                .andExpect(jsonPath("$.valorAcumulado.compras").value(2));
    }

    @Test
    void devolverUnaParteNoBajaLaFrecuenciaPeroAnularElRestoSi() throws Exception {
        ingesta.recibir(ejemplo("compra-ana.json"));

        anularTotal(ejemplo("anulacion-parcial.json"));
        mvc.perform(get(RUTA).param("identificador", ID_ANA))
                .andExpect(jsonPath("$.frecuencia").value(1))
                .andExpect(jsonPath("$.ticketPromedio.valor").value(300.00));

        // Lo que sigue vigente de la compra son 300.00: la anulación total revierte eso.
        anularTotal(ejemplo("anulacion-total.json"));

        mvc.perform(get(RUTA).param("identificador", ID_ANA))
                .andExpect(jsonPath("$.frecuencia").value(0))
                .andExpect(jsonPath("$.ticketPromedio.sinDatos").value(true))
                .andExpect(jsonPath("$.valorAcumulado.sinDatos").value(true))
                .andExpect(jsonPath("$.recencia.sinDatos").value(true));
    }

    // --- Idempotencia: una anulación repetida no descuenta dos veces ---

    @Test
    void unaAnulacionRepetidaNoDescuentaLaFrecuenciaDosVeces() throws Exception {
        ingesta.recibir(ejemplo("compra-ana.json"));
        ingesta.recibir(ejemplo("compra-carlos.json"));
        String anulacion = ejemplo("anulacion-total-carlos.json");
        anularTotal(anulacion);

        ingesta.recibirAnulacion(anulacion);

        assertThat(eventos.findAll()).filteredOn(e -> e.getEstado() == EstadoEvento.DESCARTADO).hasSize(1);
        mvc.perform(get(RUTA).param("identificador", ID_ANA, ID_CARLOS))
                .andExpect(jsonPath("$.frecuencia").value(1));
    }

    @Test
    void unaAnulacionRechazadaNoAlteraLaFrecuencia() throws Exception {
        ingesta.recibir(ejemplo("compra-ana.json"));

        // El monto no cuadra con lo vigente de la compra: el evento queda FALLIDO y nada cambia.
        ingesta.recibirAnulacion(ejemplo("anulacion-total.json"));

        assertThat(eventos.findAll()).filteredOn(e -> e.getEstado() == EstadoEvento.FALLIDO).hasSize(1);
        mvc.perform(get(RUTA).param("identificador", ID_ANA))
                .andExpect(jsonPath("$.frecuencia").value(1))
                .andExpect(jsonPath("$.ticketPromedio.valor").value(350.50));
    }

    // --- Clientes inactivos: el listado persistido se corrige de inmediato ---

    @Test
    void anularLaUnicaCompraDeUnClienteInactivoLoQuitaDelListadoSinEsperarLaDeteccionProgramada() throws Exception {
        ingesta.recibir(compraDe("11111111-1111-4111-8111-111111111111", "V-VIEJA-1", "2026-03-13T10:00:00Z"));
        detector.detectar();
        mvc.perform(get(RUTA_INACTIVOS)).andExpect(jsonPath("$.total").value(1));

        anularTotal(anulacionDe("V-VIEJA-1", "V-VIEJA-1-A1", "aaaaaaa1-1111-4111-8111-111111111111"));

        // Sin llamar a detectar(): sin compras vigentes no hay última compra desde la cual medir.
        mvc.perform(get(RUTA_INACTIVOS)).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void anularLaUltimaCompraDeUnClienteActivoLoDejaInactivoConLaCompraAnterior() throws Exception {
        ingesta.recibir(compraDe("22222222-2222-4222-8222-222222222222", "V-VIEJA-2", "2026-03-13T10:00:00Z"));
        ingesta.recibir(compraDe("33333333-3333-4333-8333-333333333333", "V-RECIENTE-2", "2026-09-25T10:00:00Z"));
        detector.detectar();
        mvc.perform(get(RUTA_INACTIVOS)).andExpect(jsonPath("$.total").value(0));

        anularTotal(anulacionDe("V-RECIENTE-2", "V-RECIENTE-2-A1", "bbbbbbb2-2222-4222-8222-222222222222"));

        mvc.perform(get(RUTA_INACTIVOS))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].idClienteOrigen").value(ID_ANA))
                .andExpect(jsonPath("$.content[0].ultimaCompra").value("2026-03-13T10:00:00Z"));
    }

    @Test
    void unaDevolucionParcialNoCambiaElListadoDeInactivos() throws Exception {
        ingesta.recibir(compraDe("44444444-4444-4444-8444-444444444444", "V-VIEJA-3", "2026-03-13T10:00:00Z"));
        detector.detectar();

        ingesta.recibirAnulacion(ejemplo("anulacion-parcial.json")
                .replace("8d2f6a4c-1b3e-4c5d-9e7f-0a1b2c3d4e5f", "ccccccc3-3333-4333-8333-333333333333")
                .replace("V-100234-D1", "V-VIEJA-3-D1")
                .replace("V-100234", "V-VIEJA-3"));
        assertThat(eventos.findAll()).allMatch(e -> e.getEstado() == EstadoEvento.PROCESADO);

        mvc.perform(get(RUTA_INACTIVOS))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].ultimaCompra").value("2026-03-13T10:00:00Z"));
    }

    // --- Utilidades ---

    /** Recibe una anulación y comprueba que quedó procesada: ningún evento descartado ni fallido. */
    private void anularTotal(String contenido) {
        ingesta.recibirAnulacion(contenido);
        assertThat(eventos.findAll()).allMatch(e -> e.getEstado() == EstadoEvento.PROCESADO);
    }

    private static String compraDe(String idEvento, String idCompra, String fecha) {
        return ejemplo("compra-ana.json")
                .replace("5b7a8c1e-3f2d-4e6a-9b1c-2d3e4f5a6b7c", idEvento)
                .replace("V-100234", idCompra)
                .replace("2026-09-27T15:28:10-04:00", fecha);
    }

    /** Anulación total de una compra de {@link #compraDe} (350.50, cliente CLI-5521). */
    private static String anulacionDe(String idCompra, String idAnulacion, String idEvento) {
        return ejemplo("anulacion-total.json")
                .replace("3a9c7e1f-5d2b-4f8a-b6c4-7e8f9a0b1c2d", idEvento)
                .replace("V-100234-A1", idAnulacion)
                .replace("V-100234", idCompra)
                .replace("300.00", "350.50");
    }
}
