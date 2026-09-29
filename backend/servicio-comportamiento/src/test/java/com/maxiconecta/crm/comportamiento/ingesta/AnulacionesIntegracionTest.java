package com.maxiconecta.crm.comportamiento.ingesta;

import com.maxiconecta.crm.comportamiento.compra.Anulacion;
import com.maxiconecta.crm.comportamiento.compra.AnulacionRepository;
import com.maxiconecta.crm.comportamiento.compra.Compra;
import com.maxiconecta.crm.comportamiento.compra.CompraRepository;
import com.maxiconecta.crm.comportamiento.compra.Origen;
import com.maxiconecta.crm.comportamiento.compra.TipoAnulacion;
import com.maxiconecta.crm.comportamiento.configuracion.ConfiguracionRabbit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

import static com.maxiconecta.crm.comportamiento.ingesta.LectorEventosTest.ejemplo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-532 · Recepción de anulaciones y devoluciones (RIO-CRM-05) con PostgreSQL y RabbitMQ reales
 * y los eventos de ejemplo del simulador.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AnulacionesIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @SpyBean
    private BitacoraIngesta bitacora;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private EventoRecibidoRepository eventos;

    @Autowired
    private CompraRepository compras;

    @Autowired
    private AnulacionRepository anulaciones;

    @Autowired
    private ReprocesadorEventos reprocesador;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transaccion;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void limpiar() {
        jdbc.execute("TRUNCATE comportamiento.intento_reproceso, comportamiento.evento_procesado, "
                + "comportamiento.anulacion_item, comportamiento.anulacion, comportamiento.compra_item, "
                + "comportamiento.compra, comportamiento.evento_recibido");
        rabbit.execute(channel -> channel.queuePurge(ConfiguracionRabbit.COLA_ANULACIONES_RESPALDO));
        doCallRealMethod().when(bitacora).registrarRecepcion(anyString());
    }

    // --- Criterio 1: la anulación queda con la compra original, la fecha, el monto revertido y el motivo ---

    @Test
    void unaDevolucionParcialQuedaRegistradaYDescuentaLaCompra() {
        compraDeVentas();

        publicarAnulacion(ejemplo("anulacion-parcial-ventas.json"));

        EventoRecibido recibido = esperarEventos(2).get(1);
        assertThat(recibido.getEstado()).isEqualTo(EstadoEvento.PROCESADO);
        assertThat(recibido.getTipoEvento()).isEqualTo("COMPRA_ANULADA");
        assertThat(recibido.getIdTransaccion()).isEqualTo("V-100234-D1");
        transaccion.executeWithoutResult(estado -> {
            Anulacion anulacion = anulaciones.findByOrigenAndIdAnulacionOrigen(Origen.VENTAS, "V-100234-D1").orElseThrow();
            assertThat(anulacion.getCompra().getIdCompraOrigen()).isEqualTo("V-100234");
            assertThat(anulacion.getTipo()).isEqualTo(TipoAnulacion.PARCIAL);
            assertThat(anulacion.getFecha()).isNotNull();
            assertThat(anulacion.getMontoRevertido()).isEqualByComparingTo("50.50");
            assertThat(anulacion.getMotivo()).isEqualTo("Accesorios con falla de fábrica");
            assertThat(anulacion.getIdEvento()).isEqualTo(recibido.getId());
            assertThat(anulacion.getItems()).extracting(i -> i.getCategoria() + " x" + i.getCantidad())
                    .containsExactly("Accesorios x2");

            Compra compra = compraV100234();
            assertThat(compra.getEstado()).isEqualTo(Compra.DEVOLUCION_PARCIAL);
            assertThat(compra.getMontoTotal()).isEqualByComparingTo("350.50");
            assertThat(compra.getMontoVigente()).isEqualByComparingTo("300.00");
        });
    }

    @Test
    void unaAnulacionTotalDespuesDeUnaDevolucionDejaLaCompraAnulada() {
        compraDeVentas();
        publicarAnulacion(ejemplo("anulacion-parcial-ventas.json"));
        esperarEventos(2);

        publicarAnulacion(ejemplo("anulacion-total-ventas.json"));

        assertThat(esperarEventos(3).get(2).getEstado()).isEqualTo(EstadoEvento.PROCESADO);
        Compra compra = compraV100234();
        assertThat(compra.getEstado()).isEqualTo(Compra.ANULADA);
        assertThat(compra.getMontoVigente()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(anulaciones.count()).isEqualTo(2);
    }

    @Test
    void unaAnulacionTotalDeMarketplaceAnulaLaCompraCompleta() {
        publicarCompra(ejemplo("compra-marketplace.json"));
        esperarEventos(1);

        publicarAnulacion(ejemplo("anulacion-total-marketplace.json"));

        assertThat(esperarEventos(2).get(1).getEstado()).isEqualTo(EstadoEvento.PROCESADO);
        assertThat(compras.findByOrigenAndIdCompraOrigen(Origen.MARKETPLACE, "MP-88120").orElseThrow().getEstado())
                .isEqualTo(Compra.ANULADA);
    }

    // --- Criterio 2: una anulación ya procesada se descarta sin efectos ---

    @Test
    void unaAnulacionRepetidaSeDescartaSinEfectos() {
        compraDeVentas();
        publicarAnulacion(ejemplo("anulacion-parcial-ventas.json"));
        esperarEventos(2);

        publicarAnulacion(ejemplo("anulacion-parcial-ventas.json"));

        EventoRecibido repetido = esperarEventos(3).get(2);
        assertThat(repetido.getEstado()).isEqualTo(EstadoEvento.DESCARTADO);
        assertThat(repetido.getCausa()).contains("La anulación VENTAS/V-100234-D1 ya fue registrada por el evento");
        assertThat(anulaciones.count()).isEqualTo(1);
        assertThat(compraV100234().getMontoVigente()).isEqualByComparingTo("300.00");
    }

    @Test
    void unReenvioConOtroIdEventoTambienSeDescarta() {
        compraDeVentas();
        publicarAnulacion(ejemplo("anulacion-total-ventas.json").replace("300.00", "350.50"));
        esperarEventos(2);

        publicarAnulacion(ejemplo("anulacion-total-ventas.json").replace("300.00", "350.50")
                .replace("3a9c7e1f-5d2b-4f8a-b6c4-7e8f9a0b1c2d", "0f1e2d3c-4b5a-4968-8776-655443322110"));

        // La compra ya está anulada, pero la anulación repetida se reconoce antes: DESCARTADO, no FALLIDO.
        assertThat(esperarEventos(3).get(2).getEstado()).isEqualTo(EstadoEvento.DESCARTADO);
        assertThat(anulaciones.count()).isEqualTo(1);
    }

    @Test
    void unaAnulacionYSuCompraConElMismoIdentificadorNoSeConfunden() {
        compraDeVentas();

        publicarAnulacion(ejemplo("anulacion-total-ventas.json").replace("V-100234-A1", "V-100234")
                .replace("300.00", "350.50"));

        assertThat(esperarEventos(2).get(1).getEstado()).isEqualTo(EstadoEvento.PROCESADO);
    }

    // --- Criterio 3: si el procesamiento falla, el evento queda disponible para reproceso ---

    @Test
    void unaAnulacionDeUnaCompraQueNoExisteQuedaFallidaYSeReprocesaCuandoLlegaLaCompra() {
        publicarAnulacion(ejemplo("anulacion-parcial-ventas.json"));

        EventoRecibido fallido = esperarEventos(1).get(0);
        assertThat(fallido.getEstado()).isEqualTo(EstadoEvento.FALLIDO);
        assertThat(fallido.getCausa())
                .isEqualTo("AnulacionInconsistenteException: La compra VENTAS/V-100234 no está registrada");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comportamiento.evento_procesado", Long.class)).isZero();

        compraDeVentas();
        IntentoReproceso intento = reprocesador.reprocesar(fallido.getId(), "admin");

        assertThat(intento.getResultado()).isEqualTo(ResultadoReproceso.PROCESADO);
        assertThat(anulaciones.count()).isEqualTo(1);
        assertThat(eventos.findById(fallido.getId()).orElseThrow().getEstado()).isEqualTo(EstadoEvento.PROCESADO);
    }

    @Test
    void unEventoInvalidoQuedaFallidoConSuContenidoOriginal() {
        compraDeVentas();
        String contenido = ejemplo("anulacion-montos-no-cuadran.json");

        publicarAnulacion(contenido);

        EventoRecibido recibido = esperarEventos(2).get(1);
        assertThat(recibido.getEstado()).isEqualTo(EstadoEvento.FALLIDO);
        assertThat(recibido.getCausa()).contains("EventoInvalidoException").contains("anulacion.montoRevertido");
        assertThat(recibido.getContenido()).isEqualTo(contenido);
        assertThat(anulaciones.count()).isZero();
        assertThat(compraV100234().getEstado()).isEqualTo(Compra.CONFIRMADA);
    }

    @Test
    void unMensajeIlegibleEnLaColaDeAnulacionesQuedaFallido() {
        publicarAnulacion(ejemplo("mensaje-ilegible.txt"));

        EventoRecibido recibido = esperarEventos(1).get(0);
        assertThat(recibido.getEstado()).isEqualTo(EstadoEvento.FALLIDO);
        assertThat(recibido.getCausa()).startsWith("EventoIlegibleException: El mensaje no es un JSON válido de anulación");
    }

    @Test
    void rechazaLoQueNoCuadraConLaCompra() {
        compraDeVentas();
        String parcial = ejemplo("anulacion-parcial-ventas.json");

        publicarAnulacion(parcial.replace("\"idCliente\": \"CLI-5521\"", "\"idCliente\": \"CLI-9999\""));
        assertThat(esperarEventos(2).get(1).getCausa())
                .contains("El cliente CLI-9999 no es el de la compra VENTAS/V-100234 (CLI-5521)");

        publicarAnulacion(parcial.replace("V-100234-D1", "V-100234-D2").replace("\"cantidad\": 2", "\"cantidad\": 3"));
        assertThat(esperarEventos(3).get(2).getCausa())
                .contains("Se devuelven 3 unidades de 'Accesorios', pero solo quedan 2 de la compra");

        publicarAnulacion(parcial.replace("V-100234-D1", "V-100234-D3").replace("Accesorios", "Hogar"));
        assertThat(esperarEventos(4).get(3).getCausa())
                .contains("La compra VENTAS/V-100234 no tiene ítems de la categoría 'Hogar'");

        publicarAnulacion(ejemplo("anulacion-total-ventas.json"));
        assertThat(esperarEventos(5).get(4).getCausa())
                .contains("La anulación total debe revertir 350.50, lo que sigue vigente de la compra VENTAS/V-100234");

        publicarAnulacion(parcial.replace("V-100234-D1", "V-100234-D4")
                .replace("2026-09-28T11:02:30-04:00", "2026-09-20T10:00:00-04:00"));
        assertThat(esperarEventos(6).get(5).getCausa()).contains("no puede ser anterior a la compra");

        assertThat(eventos.findAll()).filteredOn(e -> e.getTipoEvento().equals("COMPRA_ANULADA"))
                .extracting(EventoRecibido::getEstado).containsOnly(EstadoEvento.FALLIDO);
        assertThat(anulaciones.count()).isZero();
        assertThat(compraV100234().getMontoVigente()).isEqualByComparingTo("350.50");
    }

    @Test
    void unaAnulacionQueNoPuedeRegistrarseTerminaEnLaColaDeFallidosYSeReinyecta() throws Exception {
        compraDeVentas();
        doThrow(new IllegalStateException("base no disponible")).when(bitacora).registrarRecepcion(anyString());

        publicarAnulacion(ejemplo("anulacion-parcial-ventas.json"));

        await().atMost(Duration.ofSeconds(15))
                .until(() -> mensajesEn(ConfiguracionRabbit.COLA_ANULACIONES_RESPALDO) == 1);
        assertThat(eventos.count()).isEqualTo(1);

        doCallRealMethod().when(bitacora).registrarRecepcion(anyString());
        mvc.perform(post("/api/comportamiento/eventos/respaldo/reinyectar").param("cola", "anulaciones")
                        .header("X-Usuario", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reinyectado").value(true));

        assertThat(esperarEventos(2).get(1).getEstado()).isEqualTo(EstadoEvento.PROCESADO);
        assertThat(mensajesEn(ConfiguracionRabbit.COLA_ANULACIONES_RESPALDO)).isZero();
        assertThat(anulaciones.count()).isEqualTo(1);
    }

    @Test
    void reinyectarDeUnaColaDesconocidaDevuelve400() throws Exception {
        mvc.perform(post("/api/comportamiento/eventos/respaldo/reinyectar").param("cola", "puntos"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/comportamiento/eventos/respaldo/reinyectar").param("cola", "anulaciones"))
                .andExpect(status().isNoContent());
    }

    // --- Utilidades ---

    private void compraDeVentas() {
        long antes = eventos.count();
        publicarCompra(ejemplo("compra-ventas.json"));
        esperarEventos((int) antes + 1);
    }

    private Compra compraV100234() {
        return compras.findByOrigenAndIdCompraOrigen(Origen.VENTAS, "V-100234").orElseThrow();
    }

    private void publicarCompra(String contenido) {
        rabbit.send(ConfiguracionRabbit.EXCHANGE_VENTAS, ConfiguracionRabbit.RUTA_COMPRA_CONFIRMADA,
                new Message(contenido.getBytes(StandardCharsets.UTF_8)));
    }

    private void publicarAnulacion(String contenido) {
        rabbit.send(ConfiguracionRabbit.EXCHANGE_VENTAS, ConfiguracionRabbit.RUTA_COMPRA_ANULADA,
                new Message(contenido.getBytes(StandardCharsets.UTF_8)));
    }

    /** Espera a que haya {@code cantidad} eventos cerrados y los devuelve en el orden en que llegaron. */
    private List<EventoRecibido> esperarEventos(int cantidad) {
        return await().atMost(Duration.ofSeconds(10))
                .until(() -> eventos.findAll().stream()
                                .filter(e -> e.getEstado() != EstadoEvento.RECIBIDO)
                                .sorted(Comparator.comparing(EventoRecibido::getId))
                                .toList(),
                        lista -> lista.size() == cantidad);
    }

    private long mensajesEn(String cola) {
        Long cantidad = rabbit.execute(channel -> channel.messageCount(cola));
        return cantidad != null ? cantidad : 0;
    }
}
