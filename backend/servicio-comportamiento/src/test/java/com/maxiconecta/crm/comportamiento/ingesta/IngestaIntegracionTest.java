package com.maxiconecta.crm.comportamiento.ingesta;

import com.maxiconecta.crm.comportamiento.compra.Compra;
import com.maxiconecta.crm.comportamiento.compra.CompraRepository;
import com.maxiconecta.crm.comportamiento.compra.Origen;
import com.maxiconecta.crm.comportamiento.configuracion.ConfiguracionRabbit;
import com.maxiconecta.crm.comportamiento.validacion.EventoInvalidoException;
import com.maxiconecta.crm.comportamiento.validacion.ReglaValidacionEvento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
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
import java.time.LocalDate;
import java.util.List;

import static com.maxiconecta.crm.comportamiento.ingesta.LectorEventosTest.ejemplo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-558 · Pruebas de integración de la recepción de eventos de venta, con PostgreSQL y
 * RabbitMQ reales y los eventos de ejemplo del simulador.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class IngestaIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    /** Regla de prueba: rechaza las compras cuyo identificador empieza con "RECHAZAR-". */
    @TestConfiguration
    static class ReglaDePrueba {
        @Bean
        ReglaValidacionEvento rechazarMarcadas() {
            return evento -> {
                if (evento.compra().idCompra().startsWith("RECHAZAR-")) {
                    throw new EventoInvalidoException("Compra marcada para rechazo en la prueba");
                }
            };
        }
    }

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private EventoRecibidoRepository eventos;

    @Autowired
    private CompraRepository compras;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transaccion;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void limpiarBase() {
        jdbc.execute("TRUNCATE comportamiento.compra_item, comportamiento.compra, comportamiento.evento_recibido");
    }

    // --- Criterio 1: el evento queda almacenado con cliente, fecha, monto, ítems y origen ---

    @Test
    void unaCompraDeVentasQuedaAlmacenadaConTodosSusDatos() {
        publicar(ejemplo("compra-ventas.json"));

        EventoRecibido recibido = esperarEstado(1).get(0);
        assertThat(recibido.getEstado()).isEqualTo(EstadoEvento.PROCESADO);
        assertThat(recibido.getOrigen()).isEqualTo("VENTAS");
        assertThat(recibido.getProcesadoEn()).isNotNull();

        transaccion.executeWithoutResult(estado -> {
            Compra compra = compras.findByOrigenAndIdCompraOrigen(Origen.VENTAS, "V-100234").orElseThrow();
            assertThat(compra.getIdClienteOrigen()).isEqualTo("CLI-5521");
            assertThat(compra.getFecha()).isNotNull();
            assertThat(compra.getMontoTotal()).isEqualByComparingTo(new BigDecimal("350.50"));
            assertThat(compra.getIdEvento()).isEqualTo(recibido.getId());
            assertThat(compra.getItems()).extracting(item -> item.getCategoria() + " x" + item.getCantidad())
                    .containsExactly("Electrónica x1", "Accesorios x2");
        });
    }

    @Test
    void unaCompraDeMarketplaceQuedaAlmacenada() {
        publicar(ejemplo("compra-marketplace.json"));

        assertThat(esperarEstado(1).get(0).getEstado()).isEqualTo(EstadoEvento.PROCESADO);
        assertThat(compras.findByOrigenAndIdCompraOrigen(Origen.MARKETPLACE, "MP-88120")).isPresent();
    }

    // --- Criterio 2: una transacción repetida no se duplica y deja traza del descarte ---

    @Test
    void unaCompraRepetidaNoSeDuplicaYQuedaDescartada() {
        publicar(ejemplo("compra-ventas.json"));
        esperarEstado(1);
        publicar(ejemplo("compra-ventas.json"));

        List<EventoRecibido> recibidos = esperarEstado(2);
        assertThat(recibidos).extracting(EventoRecibido::getEstado)
                .containsExactlyInAnyOrder(EstadoEvento.PROCESADO, EstadoEvento.DESCARTADO);
        assertThat(recibidos).filteredOn(e -> e.getEstado() == EstadoEvento.DESCARTADO)
                .singleElement().extracting(EventoRecibido::getCausa).asString().contains("ya fue registrada");
        assertThat(compras.count()).isEqualTo(1);
    }

    // --- Criterio 3: un error deja el evento fallido y disponible para reproceso ---

    @Test
    void unEventoIncompletoQuedaFallidoConSuCausaYSuContenido() {
        String contenido = ejemplo("compra-sin-cliente.json");
        publicar(contenido);

        EventoRecibido recibido = esperarEstado(1).get(0);
        assertThat(recibido.getEstado()).isEqualTo(EstadoEvento.FALLIDO);
        assertThat(recibido.getCausa()).contains("compra.idCliente");
        assertThat(recibido.getContenido()).isEqualTo(contenido);
        assertThat(compras.count()).isZero();
    }

    @Test
    void unMensajeQueNoEsJsonQuedaFallidoConElTextoOriginal() {
        String contenido = ejemplo("mensaje-ilegible.txt");
        publicar(contenido);

        EventoRecibido recibido = esperarEstado(1).get(0);
        assertThat(recibido.getEstado()).isEqualTo(EstadoEvento.FALLIDO);
        assertThat(recibido.getCausa()).startsWith("EventoIlegibleException: El mensaje no es un JSON válido");
        assertThat(recibido.getContenido()).isEqualTo(contenido);
    }

    @Test
    void unEventoQueNoPasaLasReglasDeValidacionNoSeAlmacena() {
        publicar(ejemplo("compra-ventas.json").replace("V-100234", "RECHAZAR-1"));

        EventoRecibido recibido = esperarEstado(1).get(0);
        assertThat(recibido.getEstado()).isEqualTo(EstadoEvento.FALLIDO);
        assertThat(recibido.getCausa()).contains("Compra marcada para rechazo");
        assertThat(compras.count()).isZero();
    }

    // --- Criterio 4: la bitácora muestra recibidos, procesados y fallidos por periodo ---

    @Test
    void laBitacoraMuestraLosEventosDelPeriodoConSuResumen() throws Exception {
        publicar(ejemplo("compra-ventas.json"));
        publicar(ejemplo("compra-marketplace.json"));
        publicar(ejemplo("compra-sin-cliente.json"));
        esperarEstado(3);
        String hoy = LocalDate.now().toString();

        mvc.perform(get("/api/comportamiento/eventos").param("desde", hoy).param("hasta", hoy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.PROCESADO").value(2))
                .andExpect(jsonPath("$.resumen.FALLIDO").value(1))
                .andExpect(jsonPath("$.resumen.DESCARTADO").value(0))
                .andExpect(jsonPath("$.total").value(3));

        mvc.perform(get("/api/comportamiento/eventos").param("estado", "FALLIDO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventos.length()").value(1))
                .andExpect(jsonPath("$.eventos[0].causa").value(org.hamcrest.Matchers.containsString("compra.idCliente")));

        mvc.perform(get("/api/comportamiento/eventos").param("origen", "marketplace"))
                .andExpect(jsonPath("$.total").value(1));

        String ayer = LocalDate.now().minusDays(1).toString();
        mvc.perform(get("/api/comportamiento/eventos").param("desde", ayer).param("hasta", ayer))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void laBitacoraRechazaUnPeriodoInvertidoOUnEstadoDesconocido() throws Exception {
        mvc.perform(get("/api/comportamiento/eventos").param("desde", "2026-09-30").param("hasta", "2026-09-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/comportamiento/eventos").param("estado", "PERDIDO"))
                .andExpect(status().isBadRequest());
    }

    // --- Utilidades ---

    private void publicar(String contenido) {
        rabbit.send(ConfiguracionRabbit.EXCHANGE_VENTAS, ConfiguracionRabbit.RUTA_COMPRA_CONFIRMADA,
                new org.springframework.amqp.core.Message(contenido.getBytes(StandardCharsets.UTF_8)));
    }

    /** Espera a que haya tantos eventos en la bitácora y a que ninguno siga en RECIBIDO. */
    private List<EventoRecibido> esperarEstado(int cantidad) {
        return await().atMost(Duration.ofSeconds(15)).until(eventos::findAll,
                lista -> lista.size() == cantidad && lista.stream().noneMatch(e -> e.getEstado() == EstadoEvento.RECIBIDO));
    }
}
