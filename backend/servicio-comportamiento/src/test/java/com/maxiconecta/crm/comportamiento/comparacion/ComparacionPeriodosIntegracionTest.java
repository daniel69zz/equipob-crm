package com.maxiconecta.crm.comportamiento.comparacion;

import com.maxiconecta.crm.comportamiento.comun.ServicioNoDisponibleException;
import com.maxiconecta.crm.comportamiento.compra.ClientePerfiles;
import com.maxiconecta.crm.comportamiento.ingesta.EstadoEvento;
import com.maxiconecta.crm.comportamiento.ingesta.EventoRecibidoRepository;
import com.maxiconecta.crm.comportamiento.ingesta.IngestaCompras;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-358 · Comparación de la frecuencia y el valor de compra entre dos periodos (SCRUM-40,
 * docs/compra/comparacion-periodos.md) con PostgreSQL real: un cliente, un segmento, periodos en
 * cero, compras anuladas y rangos inválidos. Los perfiles 101, 102 y 103 son los de CLI-1, CLI-2 y
 * CLI-3; la comparación por segmento solo suma a los clientes que devuelve el servicio de
 * segmentación. Periodo A: agosto de 2026; periodo B: septiembre de 2026.
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ComparacionPeriodosIntegracionTest {

    private static final String RUTA_CLIENTE = "/api/comportamiento/clientes/42/comparacion-periodos";
    private static final String RUTA_SEGMENTO = "/api/comportamiento/comparacion-periodos";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private ClientePerfiles perfiles;

    @MockBean
    private ClientesDelSegmento clientesDelSegmento;

    @Autowired
    private IngestaCompras ingesta;

    @Autowired
    private EventoRecibidoRepository eventos;

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
        when(perfiles.buscar("CLI-1")).thenReturn(Optional.of(101L));
        when(perfiles.buscar("CLI-2")).thenReturn(Optional.of(102L));
        when(perfiles.buscar("CLI-3")).thenReturn(Optional.of(103L));
    }

    private static MockHttpServletRequestBuilder periodos(MockHttpServletRequestBuilder solicitud) {
        return solicitud.param("desdeA", "2026-08-01").param("hastaA", "2026-08-31")
                .param("desdeB", "2026-09-01").param("hastaB", "2026-09-30");
    }

    // --- Criterio 1: dos periodos y un cliente, con la variación absoluta y porcentual de cada métrica ---

    @Test
    void comparaLaFrecuenciaYElValorDeUnClienteEntreDosPeriodos() throws Exception {
        comprar("V-1", "CLI-1", "2026-08-05T10:00:00-04:00", "100.00");
        comprar("V-2", "CLI-1", "2026-08-20T10:00:00-04:00", "200.00");
        comprar("V-3", "CLI-1", "2026-09-10T10:00:00-04:00", "600.00");
        comprar("V-FUERA", "CLI-1", "2026-10-10T10:00:00-04:00", "9999.00");
        comprar("V-OTRO", "CLI-2", "2026-09-10T10:00:00-04:00", "9999.00");

        mvc.perform(periodos(get(RUTA_CLIENTE).param("identificador", "CLI-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alcance.tipo").value("CLIENTE"))
                .andExpect(jsonPath("$.periodoA.desde").value("2026-08-01"))
                .andExpect(jsonPath("$.periodoB.hasta").value("2026-09-30"))
                .andExpect(jsonPath("$.frecuencia.periodoA").value(2))
                .andExpect(jsonPath("$.frecuencia.periodoB").value(1))
                .andExpect(jsonPath("$.frecuencia.variacionAbsoluta").value(-1))
                .andExpect(jsonPath("$.frecuencia.variacionPorcentual").value(-50.00))
                .andExpect(jsonPath("$.frecuencia.tendencia").value("DECAE"))
                .andExpect(jsonPath("$.valor.periodoA").value(300.00))
                .andExpect(jsonPath("$.valor.periodoB").value(600.00))
                .andExpect(jsonPath("$.valor.variacionAbsoluta").value(300.00))
                .andExpect(jsonPath("$.valor.variacionPorcentual").value(100.00))
                .andExpect(jsonPath("$.valor.tendencia").value("CRECE"))
                .andExpect(jsonPath("$.sinDatos").value(false));
    }

    @Test
    void sumaLasComprasDeTodosLosIdentificadoresDelCliente() throws Exception {
        comprar("V-1", "CLI-1", "2026-08-05T10:00:00-04:00", "100.00");
        comprar("V-2", "mp-user-1", "2026-09-05T10:00:00-04:00", "50.00");
        comprar("V-3", "CLI-1", "2026-09-06T10:00:00-04:00", "70.00");

        mvc.perform(periodos(get(RUTA_CLIENTE).param("identificador", "CLI-1", "mp-user-1")))
                .andExpect(jsonPath("$.frecuencia.periodoA").value(1))
                .andExpect(jsonPath("$.frecuencia.periodoB").value(2))
                .andExpect(jsonPath("$.valor.periodoB").value(120.00))
                .andExpect(jsonPath("$.valor.variacionPorcentual").value(20.00));
    }

    @Test
    void unaCompraAnuladaNoCuentaEnNingunPeriodoYUnaDevolucionParcialDescuentaLoDevuelto() throws Exception {
        comprar("V-1", "CLI-1", "2026-08-05T10:00:00-04:00", "100.00");
        comprar("V-2", "CLI-1", "2026-08-06T10:00:00-04:00", "400.00");
        anularTotal("V-2", "CLI-1", "400.00", "2026-08-10T10:00:00-04:00");
        comprarConDosItems("V-3", "CLI-1", "2026-09-05T10:00:00-04:00", "300.00", "100.00");
        devolverAccesorios("V-3", "CLI-1", "100.00", "2026-09-08T10:00:00-04:00");

        mvc.perform(periodos(get(RUTA_CLIENTE).param("identificador", "CLI-1")))
                .andExpect(jsonPath("$.frecuencia.periodoA").value(1))
                .andExpect(jsonPath("$.frecuencia.periodoB").value(1))
                .andExpect(jsonPath("$.frecuencia.tendencia").value("ESTABLE"))
                .andExpect(jsonPath("$.valor.periodoA").value(100.00))
                .andExpect(jsonPath("$.valor.periodoB").value(300.00))
                .andExpect(jsonPath("$.valor.variacionPorcentual").value(200.00));
    }

    // --- Criterio 3: un periodo sin compras aparece en cero y la variación se calcula sin error ---

    @Test
    void unPeriodoBaseSinComprasApareceEnCeroYSuPorcentajeNoSeCalcula() throws Exception {
        comprar("V-1", "CLI-1", "2026-09-05T10:00:00-04:00", "250.00");

        mvc.perform(periodos(get(RUTA_CLIENTE).param("identificador", "CLI-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frecuencia.periodoA").value(0))
                .andExpect(jsonPath("$.frecuencia.variacionAbsoluta").value(1))
                .andExpect(jsonPath("$.frecuencia.variacionPorcentual").doesNotExist())
                .andExpect(jsonPath("$.frecuencia.tendencia").value("NUEVO"))
                .andExpect(jsonPath("$.valor.periodoA").value(0))
                .andExpect(jsonPath("$.valor.variacionAbsoluta").value(250.00))
                .andExpect(jsonPath("$.valor.tendencia").value("NUEVO"))
                .andExpect(jsonPath("$.sinDatos").value(false));
    }

    @Test
    void unPeriodoComparadoSinComprasEsUnaCaidaDeCienPorCiento() throws Exception {
        comprar("V-1", "CLI-1", "2026-08-05T10:00:00-04:00", "250.00");

        mvc.perform(periodos(get(RUTA_CLIENTE).param("identificador", "CLI-1")))
                .andExpect(jsonPath("$.valor.periodoB").value(0))
                .andExpect(jsonPath("$.valor.variacionPorcentual").value(-100.00))
                .andExpect(jsonPath("$.valor.tendencia").value("DECAE"))
                .andExpect(jsonPath("$.frecuencia.variacionPorcentual").value(-100.00));
    }

    @Test
    void sinComprasEnNingunPeriodoSeAvisaConUnMensajeYNoEsUnError() throws Exception {
        mvc.perform(periodos(get(RUTA_CLIENTE).param("identificador", "CLI-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinDatos").value(true))
                .andExpect(jsonPath("$.mensaje").value(Matchers.containsString("ninguno de los dos periodos")))
                .andExpect(jsonPath("$.valor.variacionAbsoluta").value(0))
                .andExpect(jsonPath("$.valor.variacionPorcentual").value(0.00))
                .andExpect(jsonPath("$.valor.tendencia").value("ESTABLE"));
        mvc.perform(periodos(get(RUTA_CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinDatos").value(true));
    }

    // --- Criterio 2: al comparar un segmento solo se agregan los clientes de ese segmento ---

    @Test
    void alCompararUnSegmentoSoloSumaALosClientesDeEseSegmento() throws Exception {
        comprar("V-1", "CLI-1", "2026-08-05T10:00:00-04:00", "100.00");
        comprar("V-2", "CLI-2", "2026-08-06T10:00:00-04:00", "300.00");
        comprar("V-3", "CLI-1", "2026-09-05T10:00:00-04:00", "200.00");
        comprar("V-4", "CLI-2", "2026-09-06T10:00:00-04:00", "400.00");
        comprar("V-5", "CLI-2", "2026-09-07T10:00:00-04:00", "100.00");
        // Fuera del segmento: un cliente con perfil que no está en él y otro sin perfil vinculado.
        comprar("V-6", "CLI-3", "2026-09-08T10:00:00-04:00", "5000.00");
        comprar("V-7", "SIN-PERFIL", "2026-09-08T10:00:00-04:00", "7000.00");
        when(clientesDelSegmento.idsDe("FRECUENTE")).thenReturn(List.of(101L, 102L));

        mvc.perform(periodos(get(RUTA_SEGMENTO).param("segmento", "FRECUENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alcance.tipo").value("SEGMENTO"))
                .andExpect(jsonPath("$.alcance.segmento").value("FRECUENTE"))
                .andExpect(jsonPath("$.alcance.clientes").value(2))
                .andExpect(jsonPath("$.frecuencia.periodoA").value(2))
                .andExpect(jsonPath("$.frecuencia.periodoB").value(3))
                .andExpect(jsonPath("$.frecuencia.variacionPorcentual").value(50.00))
                .andExpect(jsonPath("$.valor.periodoA").value(400.00))
                .andExpect(jsonPath("$.valor.periodoB").value(700.00))
                .andExpect(jsonPath("$.valor.variacionAbsoluta").value(300.00))
                .andExpect(jsonPath("$.valor.variacionPorcentual").value(75.00))
                .andExpect(jsonPath("$.valor.tendencia").value("CRECE"));
    }

    @Test
    void lasComprasAnuladasDelSegmentoNoSeIncluyen() throws Exception {
        comprar("V-1", "CLI-1", "2026-08-05T10:00:00-04:00", "100.00");
        comprar("V-2", "CLI-2", "2026-08-06T10:00:00-04:00", "300.00");
        anularTotal("V-2", "CLI-2", "300.00", "2026-08-07T10:00:00-04:00");
        when(clientesDelSegmento.idsDe("FRECUENTE")).thenReturn(List.of(101L, 102L));

        mvc.perform(periodos(get(RUTA_SEGMENTO).param("segmento", "FRECUENTE")))
                .andExpect(jsonPath("$.frecuencia.periodoA").value(1))
                .andExpect(jsonPath("$.valor.periodoA").value(100.00));
    }

    @Test
    void unSegmentoSinClientesDevuelveCeroConUnMensaje() throws Exception {
        comprar("V-1", "CLI-1", "2026-08-05T10:00:00-04:00", "100.00");
        when(clientesDelSegmento.idsDe("VACIO")).thenReturn(List.of());

        mvc.perform(periodos(get(RUTA_SEGMENTO).param("segmento", "VACIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alcance.clientes").value(0))
                .andExpect(jsonPath("$.sinDatos").value(true))
                .andExpect(jsonPath("$.mensaje").value(Matchers.containsString("no tiene clientes")));
    }

    @Test
    void siSegmentacionNoRespondeSeResponde503EnVezDeUnResultadoEnCero() throws Exception {
        when(clientesDelSegmento.idsDe("FRECUENTE"))
                .thenThrow(new ServicioNoDisponibleException("No se pudo consultar el segmento 'FRECUENTE'",
                        new RuntimeException()));

        mvc.perform(periodos(get(RUTA_SEGMENTO).param("segmento", "FRECUENTE")))
                .andExpect(status().isServiceUnavailable());
    }

    // --- Entradas inválidas ---

    @Test
    void losPeriodosInvalidosSeRechazanConUn400() throws Exception {
        mvc.perform(get(RUTA_CLIENTE).param("identificador", "CLI-1").param("desdeA", "2026-08-01")
                        .param("hastaA", "2026-09-15").param("desdeB", "2026-09-01").param("hastaB", "2026-09-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(Matchers.containsString("superponerse")));
        mvc.perform(get(RUTA_CLIENTE).param("identificador", "CLI-1").param("desdeA", "2026-08-31")
                        .param("hastaA", "2026-08-01").param("desdeB", "2026-09-01").param("hastaB", "2026-09-30"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(RUTA_CLIENTE).param("identificador", "CLI-1").param("desdeA", "2026-08-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(RUTA_SEGMENTO).param("desdeA", "2026-08-01").param("hastaA", "2026-08-31")
                        .param("desdeB", "2026-09-01").param("hastaB", "2026-09-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(Matchers.containsString("segmento")));
    }

    // --- Utilidades ---

    private void comprar(String idCompra, String idCliente, String fecha, String monto) {
        ingesta.recibir(compra(idCompra, idCliente, fecha, monto,
                "{ \"categoria\": \"Electrónica\", \"cantidad\": 1, \"monto\": " + monto + " }"));
        exigirProcesados();
    }

    private void comprarConDosItems(String idCompra, String idCliente, String fecha, String montoElectronica,
                                    String montoAccesorios) {
        String total = new java.math.BigDecimal(montoElectronica).add(new java.math.BigDecimal(montoAccesorios))
                .toPlainString();
        ingesta.recibir(compra(idCompra, idCliente, fecha, total,
                "{ \"categoria\": \"Electrónica\", \"cantidad\": 1, \"monto\": " + montoElectronica + " }, "
                        + "{ \"categoria\": \"Accesorios\", \"cantidad\": 2, \"monto\": " + montoAccesorios + " }"));
        exigirProcesados();
    }

    private void anularTotal(String idCompra, String idCliente, String montoRevertido, String fecha) {
        ingesta.recibirAnulacion(anulacion(idCompra + "-A", idCompra, idCliente, "TOTAL", montoRevertido, fecha, ""));
        exigirProcesados();
    }

    private void devolverAccesorios(String idCompra, String idCliente, String monto, String fecha) {
        String items = ", \"items\": [ { \"categoria\": \"Accesorios\", \"cantidad\": 2, \"monto\": " + monto + " } ]";
        ingesta.recibirAnulacion(anulacion(idCompra + "-D", idCompra, idCliente, "PARCIAL", monto, fecha, items));
        exigirProcesados();
    }

    private void exigirProcesados() {
        assertThat(eventos.findAll()).allMatch(e -> e.getEstado() == EstadoEvento.PROCESADO);
    }

    private static String compra(String idCompra, String idCliente, String fecha, String montoTotal, String items) {
        return """
                {
                  "idEvento": "%s",
                  "tipoEvento": "COMPRA_CONFIRMADA",
                  "fechaEmision": "%s",
                  "compra": {
                    "idCompra": "%s",
                    "idCliente": "%s",
                    "fecha": "%s",
                    "montoTotal": %s,
                    "items": [ %s ]
                  }
                }
                """.formatted(UUID.randomUUID(), fecha, idCompra, idCliente, fecha, montoTotal, items);
    }

    private static String anulacion(String idAnulacion, String idCompra, String idCliente, String tipo,
                                    String montoRevertido, String fecha, String items) {
        return """
                {
                  "idEvento": "%s",
                  "tipoEvento": "COMPRA_ANULADA",
                  "fechaEmision": "%s",
                  "anulacion": {
                    "idAnulacion": "%s",
                    "idCompra": "%s",
                    "idCliente": "%s",
                    "tipo": "%s",
                    "fecha": "%s",
                    "montoRevertido": %s,
                    "motivo": "Prueba de comparacion de periodos"%s
                  }
                }
                """.formatted(UUID.randomUUID(), fecha, idAnulacion, idCompra, idCliente, tipo, fecha,
                montoRevertido, items);
    }
}
