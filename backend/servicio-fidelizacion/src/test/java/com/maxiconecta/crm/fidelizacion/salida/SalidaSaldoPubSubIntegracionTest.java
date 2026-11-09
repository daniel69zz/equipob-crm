package com.maxiconecta.crm.fidelizacion.salida;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.PubSubAdmin;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.maxiconecta.crm.fidelizacion.movimiento.ClientesPerfil;
import com.maxiconecta.crm.fidelizacion.movimiento.IngestaMovimientos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.PubSubEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** SCRUM-622: emulador oficial; se inspecciona la suscripcion sin confirmar/consumir mensajes. */
@SpringBootTest(properties = {"fidelizacion.salida.enabled=true", "fidelizacion.salida.intervalo-ms=3600000",
        "spring.rabbitmq.listener.simple.auto-startup=false", "spring.cloud.gcp.core.enabled=true",
        "spring.cloud.gcp.pubsub.enabled=true"})
@Testcontainers(disabledWithoutDocker = true)
class SalidaSaldoPubSubIntegracionTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    static final PubSubEmulatorContainer PUBSUB = new PubSubEmulatorContainer(
            DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators"));

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registro) {
        registro.add("spring.cloud.gcp.pubsub.emulator-host", PUBSUB::getEmulatorEndpoint);
        registro.add("spring.cloud.gcp.project-id", () -> "crm-prueba");
    }

    @MockBean ClientesPerfil perfiles;
    @Autowired PubSubAdmin admin;
    @Autowired PubSubTemplate pubsub;
    @Autowired IngestaMovimientos ingesta;
    @Autowired PublicadorSaldoPubSub publicador;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void publicaSaldoEnElTopicoDelGrupoSinAckDeLaSuscripcion() throws Exception {
        admin.createTopic("ucb-sis323-sub-b");
        admin.createSubscription("ucb-sis323-sub-b-sub", "ucb-sis323-sub-b");
        when(perfiles.buscar(anyString())).thenReturn(Optional.of(42L));
        ingesta.recibir(SalidaSaldoIntegracionTest.evento("EMULADOR-1", "ACUMULACION", "100", "0", null,
                OffsetDateTime.now()));
        publicador.publicarPendientes();
        var mensajes = pubsub.pull("ucb-sis323-sub-b-sub", 1, false);
        assertThat(mensajes).hasSize(1);
        var mensaje = mensajes.get(0).getPubsubMessage();
        var cuerpo = json.readTree(mensaje.getData().toStringUtf8());
        assertThat(mensaje.getAttributesOrThrow("ruta")).isEqualTo("puntos.saldo.actualizado");
        assertThat(cuerpo.get("saldo").asLong()).isEqualTo(10);
        assertThat(cuerpo.get("idCliente").asText()).isEqualTo("CLI-5521");
        assertThat(cuerpo.get("idEvento").asText()).isEqualTo(jdbc.queryForObject(
                "SELECT id_evento::text FROM fidelizacion.salida_saldo_puntos", String.class));
        assertThat(jdbc.queryForObject("SELECT id_mensaje FROM fidelizacion.salida_saldo_puntos", String.class))
                .isEqualTo(mensaje.getMessageId());
        // No ack: devolverlo inmediatamente permite demostrar que sigue disponible.
        mensajes.get(0).modifyAckDeadline(0).get();
        assertThat(pubsub.pull("ucb-sis323-sub-b-sub", 1, false)).hasSize(1);
    }
}
