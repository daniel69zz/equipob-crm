package com.maxiconecta.crm.segmentacion.asignacion;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.RabbitRetryTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;

import java.util.Map;

/**
 * Colas de la asignación automática de segmento (SCRUM-26, docs/segmentacion/asignacion-automatica.md).
 * Las métricas llegan del exchange que publica servicio-comportamiento; un mensaje que no se puede
 * procesar pasa a la cola de respaldo para su reproceso.
 */
@Configuration
public class ConfiguracionRabbit {

    public static final String EXCHANGE_CRM = "crm.comportamiento.eventos";
    public static final String RUTA_METRICAS_ACTUALIZADAS = "metricas.actualizadas";
    public static final String COLA_METRICAS = "crm.segmentacion.metricas";
    public static final String EXCHANGE_RESPALDO = "crm.segmentacion.respaldo";
    public static final String COLA_METRICAS_RESPALDO = "crm.segmentacion.metricas.respaldo";

    @Bean
    public TopicExchange exchangeCrm() {
        return new TopicExchange(EXCHANGE_CRM, true, false);
    }

    @Bean
    public Queue colaMetricas() {
        return QueueBuilder.durable(COLA_METRICAS)
                .deadLetterExchange(EXCHANGE_RESPALDO)
                .deadLetterRoutingKey(COLA_METRICAS_RESPALDO)
                .build();
    }

    @Bean
    public Binding enlaceMetricas(Queue colaMetricas, TopicExchange exchangeCrm) {
        return BindingBuilder.bind(colaMetricas).to(exchangeCrm).with(RUTA_METRICAS_ACTUALIZADAS);
    }

    @Bean
    public DirectExchange exchangeRespaldo() {
        return new DirectExchange(EXCHANGE_RESPALDO, true, false);
    }

    @Bean
    public Queue colaMetricasRespaldo() {
        return QueueBuilder.durable(COLA_METRICAS_RESPALDO).build();
    }

    @Bean
    public Binding enlaceMetricasRespaldo(Queue colaMetricasRespaldo, DirectExchange exchangeRespaldo) {
        return BindingBuilder.bind(colaMetricasRespaldo).to(exchangeRespaldo).with(COLA_METRICAS_RESPALDO);
    }

    /** Un mensaje inválido no se reintenta (reintentarlo no lo vuelve válido): va directo a respaldo. */
    @Bean
    public RabbitRetryTemplateCustomizer sinReintentarMensajesInvalidos(RabbitProperties propiedades) {
        int intentos = propiedades.getListener().getSimple().getRetry().getMaxAttempts();
        return (destino, plantilla) -> {
            if (destino == RabbitRetryTemplateCustomizer.Target.LISTENER) {
                plantilla.setRetryPolicy(new SimpleRetryPolicy(intentos,
                        Map.of(MensajeMetricasInvalidoException.class, false), true, true));
            }
        };
    }
}
