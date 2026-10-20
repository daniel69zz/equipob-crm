package com.maxiconecta.crm.comportamiento.ingesta;

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
import static org.mockito.Mockito.never;
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

    @MockBean
    private ClientePerfiles perfiles;

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
        jdbc.execute("TRUNCATE comportamiento.frecuencia_compra, comportamiento.intento_reproceso, comportamiento.evento_procesado, comportamiento.anulacion_item, "
                + "comportamiento.anulacion, "
                + "comportamiento.compra_item, comportamiento.compra, comportamiento.evento_recibido");
    }

    @Test
    void unEventoFallidoSeReprocesaYRegistraUsuarioEHistorial() throws Exception {
        Long idEvento = fallido(ejemplo("compra-ana.json"), "SQLException: conexión temporal");

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

        mvc.perform(get("/api/comportamiento/eventos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventos[0].reprocesado").value(true))
                .andExpect(jsonPath("$.eventos[0].enRevision").value(false));

        mvc.perform(get("/api/comportamiento/eventos/{id}/intentos", idEvento))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].numero").value(1))
                .andExpect(jsonPath("$[0].resultado").value("PROCESADO"))
                .andExpect(jsonPath("$[0].usuario").value("admin"));
    }

    @Test
    void unEventoQueSigueInvalidoVuelveAFallarYAcumulaIntentosConCausa() throws Exception {
        String invalido = ejemplo("compra-ana.json")
                .replace("5b7a8c1e-3f2d-4e6a-9b1c-2d3e4f5a6b7c", "id-no-es-uuid");
        Long idEvento = fallido(invalido, "EventoInvalidoException: idEvento inválido");

        mvc.perform(get("/api/comportamiento/eventos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventos[0].reprocesado").value(false))
                .andExpect(jsonPath("$.eventos[0].enRevision").value(false));

        mvc.perform(post("/api/comportamiento/eventos/{id}/reprocesar", idEvento)
                        .header("X-Usuario", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.resultado").value("FALLIDO"))
                .andExpect(jsonPath("$.causa").value(org.hamcrest.Matchers.containsString("UUID")));

        mvc.perform(get("/api/comportamiento/eventos").param("estado", "FALLIDO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.eventos[0].estado").value("FALLIDO"))
                .andExpect(jsonPath("$.eventos[0].reprocesado").value(true))
                .andExpect(jsonPath("$.eventos[0].enRevision").value(false));
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
        Long procesado = bitacora.registrarRecepcion(ejemplo("compra-ana.json"));
        ingesta.procesarRegistrado(procesado);

        Long descartado = bitacora.registrarRecepcion(ejemplo("compra-ana.json"));
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
        Long original = bitacora.registrarRecepcion(ejemplo("compra-ana.json"));
        ingesta.procesarRegistrado(original);
        Long fallido = fallido(ejemplo("compra-ana.json"), "TimeoutException: temporal");

        IntentoReproceso intento = reprocesador.reprocesar(fallido, "admin");

        assertThat(intento.getResultado()).isEqualTo(ResultadoReproceso.DESCARTADO);
        assertThat(eventos.findById(fallido).orElseThrow().getEstado()).isEqualTo(EstadoEvento.DESCARTADO);
        assertThat(compras.count()).isEqualTo(1);
    }

    @Test
    void noPermiteDosReprocesosSimultaneosDelMismoEvento() throws Exception {
        Long idEvento = fallido(ejemplo("compra-carlos.json"), "TimeoutException: temporal");
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
        Message primero = new Message(ejemplo("compra-carlos.json").getBytes(StandardCharsets.UTF_8));
        Message segundo = new Message(ejemplo("compra-ana.json").getBytes(StandardCharsets.UTF_8));
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
                new Message(ejemplo("compra-ana.json").getBytes(StandardCharsets.UTF_8)));

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

    @Test
    void sinFechasIncluyeEventosAntiguosYPaginaSinPerderElTotal() throws Exception {
        Long antiguo = fallido("{}", "Evento antiguo de prueba");
        Long reciente = fallido("{}", "Evento reciente de prueba");
        jdbc.update("UPDATE comportamiento.evento_recibido SET recibido_en = ?::timestamptz WHERE id = ?",
                "2026-01-15T12:00:00Z", antiguo);
        jdbc.update("UPDATE comportamiento.evento_recibido SET recibido_en = ?::timestamptz WHERE id = ?",
                "2026-10-20T12:00:00Z", reciente);

        mvc.perform(get("/api/comportamiento/eventos").param("tamanio", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").isEmpty())
                .andExpect(jsonPath("$.hasta").isEmpty())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.resumen.FALLIDO").value(2))
                .andExpect(jsonPath("$.eventos.length()").value(1))
                .andExpect(jsonPath("$.eventos[0].id").value(reciente));
        mvc.perform(get("/api/comportamiento/eventos").param("tamanio", "1").param("pagina", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.eventos[0].id").value(antiguo));

        mvc.perform(get("/api/comportamiento/eventos").param("desde", "2026-10-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasta").isEmpty())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.eventos[0].id").value(reciente));
        mvc.perform(get("/api/comportamiento/eventos").param("hasta", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").isEmpty())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.eventos[0].id").value(antiguo));
        mvc.perform(get("/api/comportamiento/eventos")
                        .param("desde", "2026-10-14").param("hasta", "2026-10-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/comportamiento/eventos")
                        .param("desde", "2026-10-20").param("hasta", "2026-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void simularEnvioARevisionDejaElEventoEnEsperaSinProcesarLaCompra() throws Exception {
        Long idEvento = fallido(ejemplo("compra-ana.json"), "Fallo temporal de prueba");
        long comprasAntes = compras.count();
        for (int repeticion = 0; repeticion < 2; repeticion++) {
            mvc.perform(post("/api/comportamiento/eventos/{id}/revision", idEvento)
                            .header("X-Usuario", "admin"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.numero").value(1))
                    .andExpect(jsonPath("$.resultado").value("EN_PROCESO"));
        }
        verify(ingesta, never()).procesarRegistrado(idEvento);
        assertThat(compras.count()).isEqualTo(comprasAntes);
        assertThat(intentos.count()).isEqualTo(1);
        assertThat(eventos.findById(idEvento).orElseThrow().getEstado()).isEqualTo(EstadoEvento.FALLIDO);
        mvc.perform(get("/api/comportamiento/eventos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.resumen.FALLIDO").value(0))
                .andExpect(jsonPath("$.resumen.EN_ESPERA").value(1))
                .andExpect(jsonPath("$.eventos[0].estado").value("EN_ESPERA"))
                .andExpect(jsonPath("$.eventos[0].reprocesado").value(true))
                .andExpect(jsonPath("$.eventos[0].enRevision").value(true));
        mvc.perform(get("/api/comportamiento/eventos").param("estado", "EN_ESPERA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/comportamiento/eventos").param("estado", "FALLIDO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(post("/api/comportamiento/eventos/{id}/reprocesar", idEvento))
                .andExpect(status().isConflict());
        assertThat(compras.count()).isEqualTo(comprasAntes);
    }

    @Test
    void enviarARevisionUnEventoInexistenteOProcesadoSeRechaza() throws Exception {
        mvc.perform(post("/api/comportamiento/eventos/{id}/revision", 999L))
                .andExpect(status().isNotFound());
        Long idEvento = bitacora.registrarRecepcion(ejemplo("compra-ana.json"));
        EventoRecibido evento = eventos.findById(idEvento).orElseThrow();
        evento.marcarProcesado();
        eventos.saveAndFlush(evento);
        mvc.perform(post("/api/comportamiento/eventos/{id}/revision", idEvento))
                .andExpect(status().isConflict());
        assertThat(intentos.count()).isZero();
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
