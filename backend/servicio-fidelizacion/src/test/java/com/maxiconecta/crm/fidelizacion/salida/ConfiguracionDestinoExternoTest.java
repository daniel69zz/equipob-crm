package com.maxiconecta.crm.fidelizacion.salida;

import com.google.cloud.spring.pubsub.core.publisher.PubSubPublisherTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** El saldo puede ir al proyecto real del curso mientras los eventos internos usan el emulador. */
class ConfiguracionDestinoExternoTest {
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(ConfiguracionDestinoExterno.class);

    @TempDir Path carpeta;

    @Test
    void sinProyectoDeSalidaUsaElMismoPubSubDeLaMensajeria() {
        contexto.withPropertyValues("fidelizacion.salida.enabled=true")
                .run(c -> assertThat(c).doesNotHaveBean(DestinoExterno.class));
    }

    @Test
    void conSalidaDeshabilitadaNoCreaElDestinoAunqueHayaProyecto() {
        contexto.withPropertyValues("fidelizacion.salida.enabled=false", "fidelizacion.salida.proyecto=proyecto-curso")
                .run(c -> assertThat(c).doesNotHaveBean(DestinoExterno.class));
    }

    @Test
    void conProyectoDeSalidaCreaSuPropioPublicadorSinReemplazarElDeLaMensajeria() throws Exception {
        Path clave = cuentaDeServicio();
        contexto.withPropertyValues("fidelizacion.salida.enabled=true", "fidelizacion.salida.proyecto=proyecto-curso",
                        "fidelizacion.salida.credenciales=" + clave)
                .run(c -> {
                    assertThat(c).hasSingleBean(DestinoExterno.class);
                    assertThat(c.getBean(DestinoExterno.class).publicador()).isInstanceOf(PubSubPublisherTemplate.class);
                    // No compite con el PubSubPublisherTemplate que autoconfigura la mensajería interna.
                    assertThat(c).doesNotHaveBean(PubSubPublisherTemplate.class);
                });
    }

    @Test
    void unArchivoDeCredencialesInexistenteImpideArrancar() {
        contexto.withPropertyValues("fidelizacion.salida.enabled=true", "fidelizacion.salida.proyecto=proyecto-curso",
                        "fidelizacion.salida.credenciales=" + carpeta.resolve("no-existe.json"))
                .run(c -> assertThat(c).hasFailed());
    }

    /** Cuenta de servicio de prueba con una clave generada al vuelo (no es una credencial real). */
    private Path cuentaDeServicio() throws Exception {
        KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
        generador.initialize(2048);
        String pem = "-----BEGIN PRIVATE KEY-----\\n"
                + Base64.getEncoder().encodeToString(generador.generateKeyPair().getPrivate().getEncoded())
                + "\\n-----END PRIVATE KEY-----\\n";
        Path archivo = carpeta.resolve("cuenta.json");
        Files.writeString(archivo, """
                {"type": "service_account", "project_id": "proyecto-curso", "private_key_id": "prueba",
                 "private_key": "%s", "client_email": "prueba@proyecto-curso.iam.gserviceaccount.com",
                 "client_id": "1", "token_uri": "https://oauth2.googleapis.com/token"}
                """.formatted(pem));
        return archivo;
    }
}
