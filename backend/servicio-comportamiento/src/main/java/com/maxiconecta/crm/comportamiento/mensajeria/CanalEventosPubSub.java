package com.maxiconecta.crm.comportamiento.mensajeria;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publicación con Google Cloud Pub/Sub (perfil {@code pubsub}). Espera la confirmación del servidor,
 * como la publicación persistente de RabbitMQ, para que un error quede registrado por quien publica.
 */
@Component
@ConditionalOnProperty(name = Mensajeria.PROPIEDAD_BROKER, havingValue = Mensajeria.PUBSUB)
public class CanalEventosPubSub implements CanalEventos {

    private final PubSubTemplate pubsub;

    public CanalEventosPubSub(PubSubTemplate pubsub) {
        this.pubsub = pubsub;
    }

    @Override
    public void publicar(String destino, String ruta, byte[] cuerpo, String idMensaje) {
        PubsubMessage mensaje = PubsubMessage.newBuilder()
                .setData(ByteString.copyFrom(cuerpo))
                .putAttributes(Mensajeria.ATRIBUTO_RUTA, ruta)
                .putAttributes("idMensaje", idMensaje)
                .putAttributes("contentType", "application/json")
                .build();
        try {
            pubsub.publish(destino, mensaje).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publicación interrumpida en " + destino, ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException("No se pudo publicar en el tópico " + destino + ": " + ex.getMessage(), ex);
        }
    }
}
