package com.maxiconecta.crm.comportamiento.mensajeria;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.support.AcknowledgeablePubsubMessage;
import com.google.pubsub.v1.PubsubMessage;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Reproceso con Pub/Sub: toma un mensaje de la suscripción de respaldo, lo vuelve a publicar en su
 * tópico original con su misma ruta y solo después lo confirma, como el reinyector de RabbitMQ. Al
 * republicarse en el tópico también lo reciben las demás suscripciones con esa ruta; todas descartan
 * por idempotencia lo que ya aplicaron.
 */
public class ReinyectorPubSub {

    private final PubSubTemplate pubsub;

    public ReinyectorPubSub(PubSubTemplate pubsub) {
        this.pubsub = pubsub;
    }

    public Optional<Reinyeccion> reinyectarUno(String suscripcionRespaldo, String topicoDestino) throws Exception {
        List<AcknowledgeablePubsubMessage> mensajes = pubsub.pull(suscripcionRespaldo, 1, true);
        if (mensajes.isEmpty()) {
            return Optional.empty();
        }
        AcknowledgeablePubsubMessage mensaje = mensajes.get(0);
        PubsubMessage original = mensaje.getPubsubMessage();
        PubsubMessage.Builder copia = PubsubMessage.newBuilder().setData(original.getData());
        original.getAttributesMap().forEach((clave, valor) -> {
            if (!clave.equals(Mensajeria.ATRIBUTO_CAUSA) && !clave.startsWith("CloudPubSubDeadLetter")) {
                copia.putAttributes(clave, valor);
            }
        });
        try {
            String id = pubsub.publish(topicoDestino, copia.build()).get(10, TimeUnit.SECONDS);
            mensaje.ack().get(10, TimeUnit.SECONDS);
            return Optional.of(new Reinyeccion(id, original.getData().size()));
        } catch (Exception ex) {
            mensaje.nack();
            throw ex;
        }
    }

    public record Reinyeccion(String idMensaje, int bytes) {
    }
}
