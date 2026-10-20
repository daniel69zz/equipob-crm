package com.maxiconecta.crm.fidelizacion.mensajeria;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.maxiconecta.crm.fidelizacion.configuracion.ConfiguracionRabbit;
import com.maxiconecta.crm.fidelizacion.movimiento.ClientesPerfil;
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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.maxiconecta.crm.fidelizacion.movimiento.LectorMovimientosTest.ejemplo;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Broker alternativo Google Cloud Pub/Sub (perfil {@code pubsub}, docs/mensajeria/pubsub.md): los
 * movimientos RIO-CRM-03 llegan por la suscripción filtrada por {@code puntos.movimiento} y acumulan
 * puntos; los de otras rutas del mismo tópico no le llegan. Emulador oficial y PostgreSQL reales.
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

    @MockBean
    private ClientesPerfil perfiles;

    @Autowired
    private PubSubTemplate pubsub;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void unaAcumulacionLlegaPorPubSubYSoloLeLleganLosMovimientos() {
        when(perfiles.buscar(anyString())).thenReturn(Optional.of(42L));
        publicar("compra.confirmada", ejemplo("movimiento-acumulacion-carlos.json"));
        publicar(ConfiguracionRabbit.RUTA_MOVIMIENTO_PUNTOS, ejemplo("movimiento-acumulacion-ana.json"));

        await().atMost(Duration.ofSeconds(30)).until(() -> jdbc.queryForList(
                "SELECT saldo FROM fidelizacion.saldo_puntos WHERE cliente_id = 42", Long.class).contains(35L));
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM fidelizacion.evento_movimiento", Long.class) == 1);
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
