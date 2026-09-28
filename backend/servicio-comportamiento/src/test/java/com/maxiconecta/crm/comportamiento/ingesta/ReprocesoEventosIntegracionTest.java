package com.maxiconecta.crm.comportamiento.ingesta;

import com.maxiconecta.crm.comportamiento.compra.CompraRepository;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.maxiconecta.crm.comportamiento.ingesta.LectorEventosTest.ejemplo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SCRUM-24 · Reproceso administrativo e integración de un mensaje desde la DLQ. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ReprocesoEventosIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @SpyBean
    private BitacoraIngesta bitacora;

    @SpyBean
    private IngestaCompras ingesta;

    @Autowired
    private ReprocesadorEventos reprocesador;

    @Autowired
    private EventoRecibidoRepository eventos;

    @Autowired
    private IntentoReprocesoRepository intentos;

    @Autowired
    private CompraRepository compras;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void limpiar() {
        rabbit.execute(channel -> {
            channel.queuePurge(ConfiguracionRabbit.COLA_COMPRAS);
            channel.queuePurge(ConfiguracionRabbit.COLA_COMPRAS_RESPALDO);
            return null;
        });
        jdbc.execute("TRUNCATE comportamiento.intento_reproceso, comportamiento.evento_procesado, "
                + "comportamiento.compra_item, comportamiento.compra, comportamiento.evento_recibido");
    }

    @Test
    void unEventoFallidoSeReprocesaYRegistraUsuarioEHistorial() throws Exception {
        Long idEvento = fallido(ejemplo("compra-ventas.json"), "SQLException: conexión temporal");

        mvc.perform(post("/api/comportamiento/eventos/{id}/reprocesar", idEvento)
                        .header("X-Usuario", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idEvento").value(idEvento))
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.resultado").value("PROCESADO"))
                .andExpect(jsonPath("$.causa").doesNotExist())
                .andExpect(jsonPath("$.usuario").value("admin"));

        EventoRecibido evento = eventos.findById(idEvento).orElseThrow();
        assertThat(evento.getEstado()).isEqualTo(EstadoEvento.PROCESADO);
        assertThat(evento.getCausa()).isNull();
        assertThat(compras.count()).isEqualTo(1);

        mvc.perform(get("/api/comportamiento/eventos/{id}/intentos", idEvento))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].numero").value(1))
                .andExpect(jsonPath("$[0].resultado").value("PROCESADO"))
                .andExpect(jsonPath("$[0].usuario").value("admin"));
    }

    @Test
    void unEventoQueSigueInvalidoVuelveAFallarYAcumulaIntentosConCausa() throws Exception {
        String invalido = ejemplo("compra-ventas.json")
                .replace("5b7a8c1e-3f2d-4e6a-9b1c-2d3e4f5a6b7c", "id-no-es-uuid");
        Long idEvento = fallido(invalido, "EventoInvalidoException: idEvento inválido");

        mvc.perform(post("/api/comportamiento/eventos/{id}/reprocesar", idEvento)
                        .header("X-Usuario", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.resultado").value("FALLIDO"))
                .andExpect(jsonPath("$.causa").value(org.hamcrest.Matchers.containsString("UUID")));
        mvc.perform(post("/api/comportamiento/eventos/{id}/reprocesar", idEvento)
                        .header("X-Usuario", "operador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(2))
                .andExpect(jsonPath("$.resultado").value("FALLIDO"))
                .andExpect(jsonPath("$.usuario").value("operador"));

        assertThat(eventos.findById(idEvento).orElseThrow().getEstado()).isEqualTo(EstadoEvento.FALLIDO);
        assertThat(compras.count()).isZero();
        assertThat(intentos.findByEvento_IdOrderByNumeroDesc(idEvento))
                .extracting(IntentoReproceso::getNumero)
                .containsExactly(2, 1);
        assertThat(intentos.findByEvento_IdOrderByNumeroDesc(idEvento))
                .allMatch(i -> i.getCausa().contains("UUID"));
    }

    @Test
    void unEventoProcesadoOUnoDescartadoNoSePuedenReprocesar() throws Exception {
        Long procesado = bitacora.registrarRecepcion(ejemplo("compra-ventas.json"));
        ingesta.procesarRegistrado(procesado);

        Long descartado = bitacora.registrarRecepcion(ejemplo("compra-ventas.json"));
        ingesta.procesarRegistrado(descartado);
        assertThat(eventos.findById(descartado).orElseThrow().getEstado()).isEqualTo(EstadoEvento.DESCARTADO);

        mvc.perform(post("/api/comportamiento/eventos/{id}/reprocesar", procesado))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/comportamiento/eventos/{id}/reprocesar", descartado))
                .andExpect(status().isConflict());
        assertThat(intentos.count()).isZero();
    }

    @Test
    void unEventoInexistenteDevuelve404EnReprocesoEHistorial() throws Exception {
        mvc.perform(post("/api/comportamiento/eventos/{id}/reprocesar", 999L))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/comportamiento/eventos/{id}/intentos", 999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void laIdempotenciaEvitaDuplicarUnaCompraDuranteElReproceso() {
        Long original = bitacora.registrarRecepcion(ejemplo("compra-ventas.json"));
        ingesta.procesarRegistrado(original);
        Long fallido = fallido(ejemplo("compra-ventas.json"), "TimeoutException: temporal");

        IntentoReproceso intento = reprocesador.reprocesar(fallido, "admin");

        assertThat(intento.getResultado()).isEqualTo(ResultadoReproceso.DESCARTADO);
        assertThat(eventos.findById(fallido).orElseThrow().getEstado()).isEqualTo(EstadoEvento.DESCARTADO);
        assertThat(compras.count()).isEqualTo(1);
    }

    @Test
    void noPermiteDosReprocesosSimultaneosDelMismoEvento() throws Exception {
        Long idEvento = fallido(ejemplo("compra-marketplace.json"), "TimeoutException: temporal");
        CountDownLatch iniciado = new CountDownLatch(1);
        CountDownLatch continuar = new CountDownLatch(1);
        doAnswer(invocacion -> {
            iniciado.countDown();
            assertThat(continuar.await(5, TimeUnit.SECONDS)).isTrue();
            return invocacion.callRealMethod();
        }).when(ingesta).procesarRegistrado(idEvento);

        CompletableFuture<IntentoReproceso> primero = CompletableFuture.supplyAsync(
                () -> reprocesador.reprocesar(idEvento, "admin-1"));
        assertThat(iniciado.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> reprocesador.reprocesar(idEvento, "admin-2"))
                .isInstanceOf(EventoNoReprocesableException.class)
                .hasMessageContaining("en curso");
        continuar.countDown();

        assertThat(primero.get(10, TimeUnit.SECONDS).getResultado()).isEqualTo(ResultadoReproceso.PROCESADO);
        assertThat(intentos.findByEvento_IdOrderByNumeroDesc(idEvento)).hasSize(1);
        assertThat(compras.count()).isEqualTo(1);
    }

    @Test
    void reinyectaSoloUnMensajeDeLaDlqYLoProcesaPorElFlujoNormal() throws Exception {
        Message primero = new Message(ejemplo("compra-marketplace.json").getBytes(StandardCharsets.UTF_8));
        Message segundo = new Message(ejemplo("compra-ventas.json").getBytes(StandardCharsets.UTF_8));
        rabbit.send(ConfiguracionRabbit.EXCHANGE_RESPALDO, ConfiguracionRabbit.COLA_COMPRAS_RESPALDO, primero);
        rabbit.send(ConfiguracionRabbit.EXCHANGE_RESPALDO, ConfiguracionRabbit.COLA_COMPRAS_RESPALDO, segundo);
        await().atMost(Duration.ofSeconds(5)).until(() -> mensajesEn(ConfiguracionRabbit.COLA_COMPRAS_RESPALDO) == 2);

        mvc.perform(post("/api/comportamiento/eventos/respaldo/reinyectar")
                        .header("X-Usuario", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reinyectado").value(true));

        await().atMost(Duration.ofSeconds(15)).until(eventos::findAll,
                lista -> lista.size() == 1 && lista.get(0).getEstado() != EstadoEvento.RECIBIDO);
        assertThat(eventos.findAll().get(0).getEstado()).isEqualTo(EstadoEvento.PROCESADO);
        assertThat(compras.count()).isEqualTo(1);
        assertThat(mensajesEn(ConfiguracionRabbit.COLA_COMPRAS_RESPALDO)).isEqualTo(1);
    }

    @Test
    void unFalloQueNoPuedeRegistrarseSeReintentaYTerminaEnLaDlq() {
        doThrow(new IllegalStateException("base no disponible"))
                .when(bitacora).registrarRecepcion(anyString());

        rabbit.send(ConfiguracionRabbit.EXCHANGE_VENTAS, ConfiguracionRabbit.RUTA_COMPRA_CONFIRMADA,
                new Message(ejemplo("compra-ventas.json").getBytes(StandardCharsets.UTF_8)));

        await().atMost(Duration.ofSeconds(15))
                .until(() -> mensajesEn(ConfiguracionRabbit.COLA_COMPRAS_RESPALDO) == 1);
        verify(bitacora, times(3)).registrarRecepcion(anyString());
        assertThat(eventos.count()).isZero();
    }

    @Test
    void reinyectarConLaDlqVaciaDevuelve204() throws Exception {
        mvc.perform(post("/api/comportamiento/eventos/respaldo/reinyectar"))
                .andExpect(status().isNoContent());
    }

    private Long fallido(String contenido, String causa) {
        Long id = bitacora.registrarRecepcion(contenido);
        bitacora.marcarFallido(id, causa);
        return id;
    }

    private long mensajesEn(String cola) {
        Long cantidad = rabbit.execute(channel -> channel.messageCount(cola));
        return cantidad != null ? cantidad : 0;
    }
}
