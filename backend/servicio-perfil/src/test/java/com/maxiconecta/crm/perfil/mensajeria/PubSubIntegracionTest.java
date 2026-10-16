package com.maxiconecta.crm.perfil.mensajeria;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import com.maxiconecta.crm.perfil.configuracion.ConfiguracionRabbit;
import com.maxiconecta.crm.perfil.sincronizacion.EventoClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.concurrent.TimeUnit;

import static com.maxiconecta.crm.perfil.Ejemplos.ejemplo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Broker alternativo Google Cloud Pub/Sub (perfil {@code pubsub}, docs/mensajeria/pubsub.md) con el
 * emulador oficial y PostgreSQL reales: los eventos de clientes llegan por la suscripción filtrada por
 * {@code cliente.*} y los de otras rutas del mismo tópico no le llegan.
 */
@SpringBootTest
@ActiveProfiles("pubsub")
@Testcontainers(disabledWithoutDocker = true)
class PubSubIntegracionTest {

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

    @Autowired
    private PubSubTemplate pubsub;

    @Autowired
    private EventoClienteRepository eventos;

    @Autowired
    private ClienteRepository clientes;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiarBase() {
        jdbc.execute("SET session_replication_role = replica; "
                + "TRUNCATE perfil.consentimiento_historial, perfil.consentimiento_alcance, perfil.consentimiento, "
                + "perfil.cambio_perfil_detalle, perfil.cambio_perfil, perfil.direccion, perfil.vinculacion_pendiente, "
                + "perfil.cliente_origen, perfil.cliente, perfil.evento_cliente RESTART IDENTITY; "
                + "SET session_replication_role = DEFAULT");
    }

    @Test
    void unClienteLlegaPorPubSubYCreaSuPerfil() {
        publicar(ConfiguracionRabbit.RUTA_CLIENTE_REGISTRADO, ejemplo("cliente-ana-alta.json"));

        await().atMost(Duration.ofSeconds(30)).until(() -> clientes.count() == 1);
        assertThat(eventos.count()).isEqualTo(1);
    }

    @Test
    void laSuscripcionSoloRecibeLasRutasDeClientes() {
        publicar("compra.confirmada", ejemplo("cliente-carlos-alta.json"));
        publicar(ConfiguracionRabbit.RUTA_CLIENTE_ACTUALIZADO, ejemplo("cliente-ana-alta.json"));

        await().atMost(Duration.ofSeconds(30)).until(() -> clientes.count() == 1);
        assertThat(eventos.count()).isEqualTo(1);
    }

    private void publicar(String ruta, String contenido) {
        try {
            pubsub.publish(ConfiguracionRabbit.EXCHANGE_VENTAS, PubsubMessage.newBuilder()
                    .setData(ByteString.copyFrom(contenido, StandardCharsets.UTF_8))
                    .putAttributes(Mensajeria.ATRIBUTO_RUTA, ruta).build()).get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
