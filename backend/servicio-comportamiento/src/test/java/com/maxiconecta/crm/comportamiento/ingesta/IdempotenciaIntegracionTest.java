package com.maxiconecta.crm.comportamiento.ingesta;

import com.maxiconecta.crm.comportamiento.compra.Compra;
import com.maxiconecta.crm.comportamiento.compra.CompraRepository;
import com.maxiconecta.crm.comportamiento.configuracion.ConfiguracionRabbit;
import com.maxiconecta.crm.comportamiento.compra.ClientePerfiles;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.maxiconecta.crm.comportamiento.ingesta.LectorEventosTest.ejemplo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-244 · Tratamiento idempotente: se reenvía el mismo evento y se verifica que no hay
 * efectos secundarios. Con varios consumidores en paralelo para cubrir copias simultáneas.
 */
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.concurrency=8",
        "spring.rabbitmq.listener.simple.prefetch=1"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class IdempotenciaIntegracionTest {

    @MockBean
    private ClientePerfiles perfiles;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private EventoRecibidoRepository eventos;

    @Autowired
    private EventoProcesadoRepository procesados;

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
        jdbc.execute("TRUNCATE comportamiento.frecuencia_compra, comportamiento.intento_reproceso, comportamiento.evento_procesado, comportamiento.anulacion_item, "
                + "comportamiento.anulacion, "
                + "comportamiento.compra_item, comportamiento.compra, comportamiento.evento_recibido");
    }

    // --- Criterio 1: un evento ya procesado se descarta sin alterar el historial ---

    @Test
    void elMismoEventoReenviadoVariasVecesSeRegistraUnaSolaVez() {
        String evento = ejemplo("compra-ana.json");
        publicar(evento);
        esperar(1);
        IntStream.range(0, 4).forEach(i -> publicar(evento));

        List<EventoRecibido> recibidos = esperar(5);
        assertThat(recibidos).filteredOn(e -> e.getEstado() == EstadoEvento.PROCESADO).hasSize(1);
        assertThat(recibidos).filteredOn(e -> e.getEstado() == EstadoEvento.DESCARTADO).hasSize(4);
        assertThat(compras.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comportamiento.compra_item", Long.class)).isEqualTo(2);
        assertThat(procesados.count()).isEqualTo(1);
    }

    @Test
    void unReenvioConOtroIdEventoYDatosDistintosNoCambiaLaCompraOriginal() {
        publicar(ejemplo("compra-ana.json"));
        esperar(1);
        String reenvio = ejemplo("compra-ana.json")
                .replace("5b7a8c1e-3f2d-4e6a-9b1c-2d3e4f5a6b7c", UUID.randomUUID().toString())
                .replace("350.50", "999.99")
                .replace("300.00", "949.49");
        publicar(reenvio);

        assertThat(esperar(2)).extracting(EventoRecibido::getEstado)
                .containsExactlyInAnyOrder(EstadoEvento.PROCESADO, EstadoEvento.DESCARTADO);
        transaccion.executeWithoutResult(estado -> {
            Compra compra = compras.findByIdCompraOrigen("V-100234").orElseThrow();
            assertThat(compra.getMontoTotal()).isEqualByComparingTo(new BigDecimal("350.50"));
            assertThat(compra.getItems()).hasSize(2);
        });
    }

    @Test
    void unCampoOrigenEnElMensajeNoHaceOtraCompra() {
        publicar(ejemplo("compra-ana.json"));
        publicar(ejemplo("compra-ana.json")
                .replace("5b7a8c1e-3f2d-4e6a-9b1c-2d3e4f5a6b7c", UUID.randomUUID().toString())
                .replace("\"tipoEvento\"", "\"origen\": \"MARKETPLACE\", \"tipoEvento\""));

        assertThat(esperar(2)).extracting(EventoRecibido::getEstado)
                .containsExactlyInAnyOrder(EstadoEvento.PROCESADO, EstadoEvento.DESCARTADO);
        assertThat(compras.count()).isEqualTo(1);
    }

    @Test
    void siElPrimerEnvioFallaElReenvioCorrectoSiSeProcesa() {
        String sinCliente = ejemplo("compra-ana.json").replace("\"idCliente\": \"CLI-5521\",", "");
        publicar(sinCliente);
        assertThat(esperar(1).get(0).getEstado()).isEqualTo(EstadoEvento.FALLIDO);

        publicar(ejemplo("compra-ana.json"));

        assertThat(esperar(2)).extracting(EventoRecibido::getEstado)
                .containsExactlyInAnyOrder(EstadoEvento.FALLIDO, EstadoEvento.PROCESADO);
        assertThat(compras.count()).isEqualTo(1);
    }

    @Test
    void veinteCopiasSimultaneasDejanUnaSolaCompraYNingunFallido() {
        String evento = ejemplo("compra-carlos.json");
        IntStream.range(0, 20).parallel().forEach(i -> publicar(evento));

        List<EventoRecibido> recibidos = esperar(20);
        assertThat(recibidos).filteredOn(e -> e.getEstado() == EstadoEvento.PROCESADO).hasSize(1);
        assertThat(recibidos).filteredOn(e -> e.getEstado() == EstadoEvento.DESCARTADO).hasSize(19);
        assertThat(recibidos).noneMatch(e -> e.getEstado() == EstadoEvento.FALLIDO);
        assertThat(compras.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comportamiento.compra_item", Long.class)).isEqualTo(2);
    }

    // --- Criterio 2: el descarte queda en la traza con identificador y fecha ---

    @Test
    void elDescarteQuedaEnLaTrazaConTransaccionYFecha() {
        publicar(ejemplo("compra-ana.json"));
        EventoRecibido original = esperar(1).get(0);
        publicar(ejemplo("compra-ana.json"));

        EventoRecibido descartado = esperar(2).stream()
                .filter(e -> e.getEstado() == EstadoEvento.DESCARTADO).findFirst().orElseThrow();
        assertThat(descartado.getIdTransaccion()).isEqualTo("V-100234");
        assertThat(descartado.getIdEventoOrigen()).isEqualTo("5b7a8c1e-3f2d-4e6a-9b1c-2d3e4f5a6b7c");
        assertThat(descartado.getProcesadoEn()).isNotNull();
        assertThat(descartado.getCausa()).isEqualTo(
                "La compra V-100234 ya fue registrada por el evento " + original.getId());
    }

    // --- Criterio 5: la bitácora muestra cuántos se descartaron por duplicidad en el periodo ---

    @Test
    void laBitacoraCuentaLosDescartadosYBuscaPorTransaccion() throws Exception {
        String evento = ejemplo("compra-ana.json");
        publicar(evento);
        esperar(1);
        publicar(evento);
        publicar(evento);
        publicar(ejemplo("compra-carlos.json"));
        esperar(4);

        mvc.perform(get("/api/comportamiento/eventos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.DESCARTADO").value(2))
                .andExpect(jsonPath("$.resumen.PROCESADO").value(2));

        mvc.perform(get("/api/comportamiento/eventos").param("transaccion", "V-100234"))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.eventos[0].idTransaccion").value("V-100234"));
    }

    // --- Utilidades ---

    private void publicar(String contenido) {
        rabbit.send(ConfiguracionRabbit.EXCHANGE_VENTAS, ConfiguracionRabbit.RUTA_COMPRA_CONFIRMADA,
                new Message(contenido.getBytes(StandardCharsets.UTF_8)));
    }

    /** Espera a que haya tantos eventos en la bitácora y a que ninguno siga en RECIBIDO. */
    private List<EventoRecibido> esperar(int cantidad) {
        return await().atMost(Duration.ofSeconds(20)).until(eventos::findAll,
                lista -> lista.size() == cantidad && lista.stream().noneMatch(e -> e.getEstado() == EstadoEvento.RECIBIDO));
    }
}
