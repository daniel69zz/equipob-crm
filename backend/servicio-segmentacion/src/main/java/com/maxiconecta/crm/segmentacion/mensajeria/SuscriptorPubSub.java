package com.maxiconecta.crm.segmentacion.mensajeria;

import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import com.google.pubsub.v1.PubsubMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.context.SmartLifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Entrega los mensajes de las suscripciones de Pub/Sub a los mismos manejadores que usan los
 * receptores de RabbitMQ, con el mismo tratamiento de errores:
 * <ul>
 *   <li>procesado sin error: se confirma ({@code ack});</li>
 *   <li>rechazado a propósito ({@link AmqpRejectAndDontRequeueException}, por ejemplo un mensaje
 *       inválido): se copia al tópico de respaldo con su causa y se confirma, sin reintentos;</li>
 *   <li>cualquier otro error (por ejemplo, la base no responde): no se confirma ({@code nack}); Pub/Sub
 *       lo reintenta y, agotados los intentos, lo pasa al tópico de respaldo.</li>
 * </ul>
 */
public class SuscriptorPubSub implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SuscriptorPubSub.class);

    private final PubSubTemplate pubsub;
    private final RecursosPubSub recursos;
    private final boolean crearRecursos;
    private final List<SuscripcionPubSub> suscripciones;
    private final List<Subscriber> activos = new ArrayList<>();

    public SuscriptorPubSub(PubSubTemplate pubsub, RecursosPubSub recursos, boolean crearRecursos,
                            List<SuscripcionPubSub> suscripciones) {
        this.pubsub = pubsub;
        this.recursos = recursos;
        this.crearRecursos = crearRecursos;
        this.suscripciones = List.copyOf(suscripciones);
    }

    @Override
    public synchronized void start() {
        for (SuscripcionPubSub suscripcion : suscripciones) {
            if (crearRecursos) {
                recursos.crear(suscripcion);
            }
            activos.add(pubsub.subscribe(suscripcion.nombre(), mensaje -> entregar(suscripcion, mensaje)));
            log.info("Escuchando la suscripción de Pub/Sub {}", suscripcion.nombre());
        }
    }

    void entregar(SuscripcionPubSub suscripcion, BasicAcknowledgeablePubsubMessage mensaje) {
        PubsubMessage contenido = mensaje.getPubsubMessage();
        String ruta = contenido.getAttributesOrDefault(Mensajeria.ATRIBUTO_RUTA, null);
        if (!suscripcion.acepta(ruta)) {
            log.debug("Mensaje {} con ruta {} ignorado por {}", contenido.getMessageId(), ruta, suscripcion.nombre());
            mensaje.ack();
            return;
        }
        try {
            suscripcion.manejador().accept(contenido.getData().toByteArray());
            mensaje.ack();
        } catch (RuntimeException ex) {
            if (esRechazo(ex)) {
                enviarARespaldo(suscripcion, contenido, ex);
                mensaje.ack();
            } else {
                log.warn("Mensaje {} de {} no procesado; Pub/Sub lo reintentará: {}", contenido.getMessageId(),
                        suscripcion.nombre(), ex.getMessage());
                mensaje.nack();
            }
        }
    }

    private void enviarARespaldo(SuscripcionPubSub suscripcion, PubsubMessage contenido, RuntimeException causa) {
        PubsubMessage copia = contenido.toBuilder().clearMessageId().clearPublishTime()
                .putAttributes(Mensajeria.ATRIBUTO_CAUSA, String.valueOf(causa.getMessage())).build();
        try {
            pubsub.publish(suscripcion.respaldo(), copia).get(10, TimeUnit.SECONDS);
            log.warn("Mensaje {} de {} enviado a respaldo: {}", contenido.getMessageId(), suscripcion.nombre(),
                    causa.getMessage());
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // Sin confirmar: Pub/Sub lo reintentará y lo pasará a respaldo con su política.
            throw new IllegalStateException("No se pudo enviar a respaldo el mensaje " + contenido.getMessageId(), ex);
        }
    }

    private static boolean esRechazo(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof AmqpRejectAndDontRequeueException) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized void stop() {
        activos.forEach(Subscriber::stopAsync);
        activos.forEach(suscriptor -> {
            try {
                suscriptor.awaitTerminated(10, TimeUnit.SECONDS);
            } catch (Exception ex) {
                log.warn("La suscripción no se detuvo a tiempo: {}", ex.getMessage());
            }
        });
        activos.clear();
    }

    @Override
    public synchronized boolean isRunning() {
        return !activos.isEmpty();
    }
}
