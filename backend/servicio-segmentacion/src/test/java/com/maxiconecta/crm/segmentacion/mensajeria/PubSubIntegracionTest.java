package com.maxiconecta.crm.segmentacion.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.support.AcknowledgeablePubsubMessage;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.maxiconecta.crm.segmentacion.asignacion.ConfiguracionRabbit;
import com.maxiconecta.crm.segmentacion.asignacion.ReinyectorRespaldo;
import com.maxiconecta.crm.segmentacion.criterio.Criterio.Condicion;
import com.maxiconecta.crm.segmentacion.criterio.Criterio.Indicador;
import com.maxiconecta.crm.segmentacion.criterio.CriterioService;
import com.maxiconecta.crm.segmentacion.criterio.SolicitudCriterio;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.PubSubEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Broker alternativo Google Cloud Pub/Sub (perfil {@code pubsub}, docs/mensajeria/pubsub.md) con el
 * emulador oficial y PostgreSQL reales: las métricas llegan por la suscripción de Pub/Sub, se asigna
 * el segmento, una reentrega no recalcula y un mensaje inválido pasa al respaldo y se puede reinyectar.
 */
@SpringBootTest
@ActiveProfiles("pubsub")
@Testcontainers
class PubSubIntegracionTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final PubSubEmulatorContainer PUBSUB = new PubSubEmulatorContainer(
            DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators"));

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registro) {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("seguridad").locations("filesystem:../api-gateway/src/main/resources/db/migration").load()
                .migrate();
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
        registro.add("spring.cloud.gcp.pubsub.emulator-host", PUBSUB::getEmulatorEndpoint);
        registro.add("spring.cloud.gcp.project-id", () -> "crm-prueba");
    }

    @Autowired
    private PubSubTemplate pubsub;

    @Autowired
    private CriterioService criterios;

    @Autowired
    private ReinyectorRespaldo reinyector;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE segmentacion.segmento_cliente, segmentacion.metricas_cliente, segmentacion.evento_procesado");
        jdbc.update("DELETE FROM segmentacion.criterio");
        criterios.crear(new SolicitudCriterio("Habituales", "", Indicador.FRECUENCIA, Condicion.ENTRE,
                new BigDecimal("2"), new BigDecimal("9"), null, null), "admin");
        pubsub.pull(ConfiguracionRabbit.COLA_METRICAS_RESPALDO, 100, true).forEach(AcknowledgeablePubsubMessage::ack);
    }

    @Test
    void lasMetricasLleganPorPubSubYAsignanElSegmentoUnaSolaVez() {
        String idEvento = UUID.randomUUID().toString();
        String mensaje = metricas(idEvento, 501, 3);
        publicar(mensaje);
        await().atMost(Duration.ofSeconds(30)).until(() -> segmento(501).equals(List.of("Habituales")));

        publicar(mensaje);
        publicar(metricas(UUID.randomUUID().toString(), 502, 3));
        await().atMost(Duration.ofSeconds(30)).until(() -> segmento(502).equals(List.of("Habituales")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM segmentacion.evento_procesado WHERE id_evento = ?",
                Long.class, idEvento)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM segmentacion.historial_segmento WHERE id_evento = ?",
                Long.class, idEvento)).isEqualTo(1);
    }

    @Test
    void unMensajeInvalidoPasaAlRespaldoYSePuedeReinyectar() {
        publicar("{ no es json");

        await().atMost(Duration.ofSeconds(30)).until(() -> reinyector.reinyectarUno("admin").isPresent());
        await().atMost(Duration.ofSeconds(30)).until(() -> !pubsub
                .pull(ConfiguracionRabbit.COLA_METRICAS_RESPALDO, 1, true).isEmpty());
    }

    private List<String> segmento(long cliente) {
        return jdbc.queryForList("SELECT segmento FROM segmentacion.segmento_cliente WHERE cliente_id = ?",
                String.class, cliente);
    }

    private String metricas(String idEvento, long cliente, long frecuencia) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idEvento", idEvento);
        datos.put("tipoEventoOrigen", "COMPRA_CONFIRMADA");
        datos.put("idCliente", cliente);
        datos.put("frecuencia", frecuencia);
        datos.put("valor", 100);
        datos.put("categorias", List.of());
        datos.put("calculadasEn", "2026-10-16T12:00:00Z");
        try {
            return json.writeValueAsString(datos);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void publicar(String contenido) {
        try {
            pubsub.publish(ConfiguracionRabbit.EXCHANGE_CRM, PubsubMessage.newBuilder()
                    .setData(ByteString.copyFrom(contenido, StandardCharsets.UTF_8))
                    .putAttributes(Mensajeria.ATRIBUTO_RUTA, ConfiguracionRabbit.RUTA_METRICAS_ACTUALIZADAS)
                    .build()).get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
