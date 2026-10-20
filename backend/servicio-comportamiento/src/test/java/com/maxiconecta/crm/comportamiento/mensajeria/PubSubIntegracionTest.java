package com.maxiconecta.crm.comportamiento.mensajeria;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.PubSubAdmin;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.support.AcknowledgeablePubsubMessage;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.maxiconecta.crm.comportamiento.compra.ClientePerfiles;
import com.maxiconecta.crm.comportamiento.compra.CompraRepository;
import com.maxiconecta.crm.comportamiento.compra.Identificador;
import com.maxiconecta.crm.comportamiento.configuracion.ConfiguracionRabbit;
import com.maxiconecta.crm.comportamiento.ingesta.ReinyectorMensajesRespaldo;
import com.maxiconecta.crm.comportamiento.ingesta.ReinyectorMensajesRespaldo.ColaRespaldo;
import com.maxiconecta.crm.comportamiento.preferencia.IdentificadoresPreferencias;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.PubSubEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.maxiconecta.crm.comportamiento.ingesta.LectorEventosTest.ejemplo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Broker alternativo Google Cloud Pub/Sub (perfil {@code pubsub}, docs/mensajeria/pubsub.md) con el
 * emulador oficial y PostgreSQL reales: las compras y anulaciones llegan por las suscripciones de
 * Pub/Sub, las métricas se publican en su tópico, las preferencias se actualizan y un mensaje
 * rechazado pasa al respaldo y se puede reinyectar. Sin RabbitMQ.
 */
@SpringBootTest
@ActiveProfiles("pubsub")
@Testcontainers(disabledWithoutDocker = true)
class PubSubIntegracionTest {

    private static final String SUSCRIPCION_PRUEBA = "prueba.metricas";
    private static final long CLIENTE = 42;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final PubSubEmulatorContainer PUBSUB = new PubSubEmulatorContainer(
            DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators"));

    @DynamicPropertySource
    static void pubsub(DynamicPropertyRegistry registro) {
        registro.add("spring.cloud.gcp.pubsub.emulator-host", PUBSUB::getEmulatorEndpoint);
        registro.add("spring.cloud.gcp.project-id", () -> "crm-prueba");
    }

    @MockBean
    private ClientePerfiles perfiles;

    @MockBean
    private IdentificadoresPreferencias identificadores;

    @Autowired
    private PubSubTemplate pubsub;

    @Autowired
    private PubSubAdmin admin;

    @Autowired
    private CompraRepository compras;

    @Autowired
    private ReinyectorMensajesRespaldo reinyector;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE comportamiento.frecuencia_compra, comportamiento.intento_reproceso, "
                + "comportamiento.evento_procesado, comportamiento.anulacion_item, comportamiento.anulacion, "
                + "comportamiento.compra_item, comportamiento.compra, comportamiento.evento_recibido, "
                + "comportamiento.actualizacion_preferencias, comportamiento.perfil_preferencias CASCADE");
        if (admin.getSubscription(SUSCRIPCION_PRUEBA) == null) {
            admin.createSubscription(SUSCRIPCION_PRUEBA, ConfiguracionRabbit.EXCHANGE_CRM);
        }
        pubsub.pull(SUSCRIPCION_PRUEBA, 100, true).forEach(AcknowledgeablePubsubMessage::ack);
        pubsub.pull(ConfiguracionRabbit.COLA_PREFERENCIAS_RESPALDO, 100, true).forEach(AcknowledgeablePubsubMessage::ack);
        when(perfiles.buscar(anyString())).thenReturn(Optional.of(CLIENTE));
        when(identificadores.obtener(CLIENTE)).thenReturn(List.of(new Identificador("CLI-5521")));
    }

    @Test
    void unaCompraLlegaPorPubSubPublicaLasMetricasYActualizaLasPreferencias() throws Exception {
        publicar(ConfiguracionRabbit.EXCHANGE_VENTAS, ConfiguracionRabbit.RUTA_COMPRA_CONFIRMADA,
                ejemplo("compra-ana.json"));

        await().atMost(Duration.ofSeconds(30)).until(() -> compras.findByIdCompraOrigen("V-100234").isPresent());
        AcknowledgeablePubsubMessage metricas = recibirMetricas();
        assertThat(metricas.getPubsubMessage().getAttributesMap())
                .containsEntry(Mensajeria.ATRIBUTO_RUTA, ConfiguracionRabbit.RUTA_METRICAS_ACTUALIZADAS);
        JsonNode cuerpo = json.readTree(metricas.getPubsubMessage().getData().toByteArray());
        assertThat(cuerpo.get("idCliente").asLong()).isEqualTo(CLIENTE);
        assertThat(cuerpo.get("valor").decimalValue()).isEqualByComparingTo("350.50");

        await().atMost(Duration.ofSeconds(30)).until(() -> versionPreferencias() == 1);
    }

    @Test
    void unaAnulacionLlegaPorSuPropiaSuscripcion() {
        publicar(ConfiguracionRabbit.EXCHANGE_VENTAS, ConfiguracionRabbit.RUTA_COMPRA_CONFIRMADA,
                ejemplo("compra-ana.json"));
        await().atMost(Duration.ofSeconds(30)).until(() -> versionPreferencias() == 1);

        publicar(ConfiguracionRabbit.EXCHANGE_VENTAS, ConfiguracionRabbit.RUTA_COMPRA_ANULADA,
                ejemplo("anulacion-parcial.json"));

        await().atMost(Duration.ofSeconds(30)).until(() -> versionPreferencias() == 2);
        assertThat(jdbc.queryForList("SELECT categoria FROM comportamiento.preferencia_categoria "
                + "WHERE cliente_id = 42 ORDER BY posicion", String.class)).containsExactly("Electrónica");
    }

    @Test
    void unMensajeRechazadoPasaAlRespaldoYSePuedeReinyectar() {
        publicar(ConfiguracionRabbit.EXCHANGE_CRM, ConfiguracionRabbit.RUTA_METRICAS_ACTUALIZADAS, "{ no es json");

        await().atMost(Duration.ofSeconds(30)).until(() -> reinyector
                .reinyectarUno(ColaRespaldo.PREFERENCIAS, "admin").isPresent());
        // Sigue siendo inválido: vuelve al respaldo, sin perderse.
        await().atMost(Duration.ofSeconds(30)).until(() -> !pubsub
                .pull(ConfiguracionRabbit.COLA_PREFERENCIAS_RESPALDO, 1, true).isEmpty());
    }

    private void publicar(String topico, String ruta, String contenido) {
        try {
            pubsub.publish(topico, PubsubMessage.newBuilder()
                    .setData(ByteString.copyFrom(contenido, StandardCharsets.UTF_8))
                    .putAttributes(Mensajeria.ATRIBUTO_RUTA, ruta).build()).get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private AcknowledgeablePubsubMessage recibirMetricas() {
        AcknowledgeablePubsubMessage[] recibido = new AcknowledgeablePubsubMessage[1];
        await().atMost(Duration.ofSeconds(30)).until(() -> {
            List<AcknowledgeablePubsubMessage> mensajes = pubsub.pull(SUSCRIPCION_PRUEBA, 1, true);
            if (mensajes.isEmpty()) {
                return false;
            }
            recibido[0] = mensajes.get(0);
            recibido[0].ack();
            return true;
        });
        return recibido[0];
    }

    private long versionPreferencias() {
        List<Long> versiones = jdbc.queryForList(
                "SELECT version FROM comportamiento.perfil_preferencias WHERE cliente_id = ?", Long.class, CLIENTE);
        return versiones.isEmpty() ? 0 : versiones.get(0);
    }
}
