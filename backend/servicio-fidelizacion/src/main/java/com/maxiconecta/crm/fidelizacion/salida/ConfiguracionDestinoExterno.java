package com.maxiconecta.crm.fidelizacion.salida;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.spring.pubsub.core.publisher.PubSubPublisherTemplate;
import com.google.cloud.spring.pubsub.support.DefaultPublisherFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Con {@code fidelizacion.salida.proyecto} el saldo se publica siempre en Google Cloud real, con sus propias
 * credenciales e independiente del emulador o proyecto de los eventos internos. No se registra como
 * {@link PubSubPublisherTemplate} para no reemplazar el publicador de la mensajería interna.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("${fidelizacion.salida.enabled:false} and '${fidelizacion.salida.proyecto:}' != ''")
class ConfiguracionDestinoExterno {
    private static final List<String> ALCANCE = List.of("https://www.googleapis.com/auth/pubsub");

    @Bean
    DestinoExterno destinoExterno(@Value("${fidelizacion.salida.proyecto}") String proyecto,
                                  @Value("${fidelizacion.salida.credenciales:}") String credenciales)
            throws IOException {
        DefaultPublisherFactory fabrica = new DefaultPublisherFactory(() -> proyecto);
        fabrica.setCredentialsProvider(FixedCredentialsProvider.create(credenciales(credenciales)));
        return new DestinoExterno(new PubSubPublisherTemplate(fabrica));
    }

    /** Archivo de cuenta de servicio fuera del repositorio o, sin archivo, las Application Default Credentials. */
    static GoogleCredentials credenciales(String archivo) throws IOException {
        if (archivo.isBlank()) {
            return GoogleCredentials.getApplicationDefault().createScoped(ALCANCE);
        }
        try (InputStream entrada = new FileInputStream(archivo)) {
            return GoogleCredentials.fromStream(entrada).createScoped(ALCANCE);
        }
    }
}
