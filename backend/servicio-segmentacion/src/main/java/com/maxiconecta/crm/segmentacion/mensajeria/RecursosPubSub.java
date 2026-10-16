package com.maxiconecta.crm.segmentacion.mensajeria;

import com.google.cloud.spring.core.GcpProjectIdProvider;
import com.google.cloud.spring.pubsub.PubSubAdmin;
import com.google.protobuf.Duration;
import com.google.pubsub.v1.DeadLetterPolicy;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.RetryPolicy;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.TopicName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Crea en Pub/Sub los tópicos y suscripciones que faltan, como RabbitMQ declara sus colas al
 * arrancar: el tópico, la suscripción filtrada por ruta con su política de reintentos y de respaldo, y
 * el tópico y la suscripción de respaldo. No modifica recursos que ya existen.
 */
public class RecursosPubSub {

    private static final Logger log = LoggerFactory.getLogger(RecursosPubSub.class);

    private final PubSubAdmin admin;
    private final String proyecto;
    private final int maxIntentos;

    public RecursosPubSub(PubSubAdmin admin, GcpProjectIdProvider proyecto, int maxIntentos) {
        this.admin = admin;
        this.proyecto = proyecto.getProjectId();
        this.maxIntentos = maxIntentos;
    }

    public void crear(SuscripcionPubSub suscripcion) {
        crearTopico(suscripcion.topico());
        crearTopico(suscripcion.respaldo());
        if (admin.getSubscription(suscripcion.nombre()) == null) {
            admin.createSubscription(Subscription.newBuilder()
                    .setName(ProjectSubscriptionName.of(proyecto, suscripcion.nombre()).toString())
                    .setTopic(TopicName.of(proyecto, suscripcion.topico()).toString())
                    .setFilter(suscripcion.filtro())
                    .setAckDeadlineSeconds(30)
                    .setRetryPolicy(RetryPolicy.newBuilder()
                            .setMinimumBackoff(Duration.newBuilder().setSeconds(2))
                            .setMaximumBackoff(Duration.newBuilder().setSeconds(60)))
                    .setDeadLetterPolicy(DeadLetterPolicy.newBuilder()
                            .setDeadLetterTopic(TopicName.of(proyecto, suscripcion.respaldo()).toString())
                            .setMaxDeliveryAttempts(maxIntentos)));
            log.info("Suscripción {} creada sobre {} ({})", suscripcion.nombre(), suscripcion.topico(),
                    suscripcion.filtro());
        }
        if (admin.getSubscription(suscripcion.respaldo()) == null) {
            admin.createSubscription(suscripcion.respaldo(), suscripcion.respaldo());
        }
    }

    private void crearTopico(String nombre) {
        if (admin.getTopic(nombre) == null) {
            admin.createTopic(nombre);
            log.info("Tópico {} creado", nombre);
        }
    }
}
