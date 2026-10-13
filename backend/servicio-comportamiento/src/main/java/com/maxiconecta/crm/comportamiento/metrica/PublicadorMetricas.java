package com.maxiconecta.crm.comportamiento.metrica;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxiconecta.crm.comportamiento.compra.Compra;
import com.maxiconecta.crm.comportamiento.configuracion.ConfiguracionRabbit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica las métricas del cliente para la asignación automática de segmento (SCRUM-262,
 * SCRUM-536; docs/segmentacion/asignacion-automatica.md).
 * <p>
 * {@link #programar} se llama dentro de la transacción de la compra o la anulación: calcula la foto
 * y la deja pendiente. Solo se envía a RabbitMQ si esa transacción se confirma, para no anunciar
 * una compra que se revirtió. Un evento descartado por duplicado no llega a programar nada.
 */
@Component
public class PublicadorMetricas {

    private static final Logger log = LoggerFactory.getLogger(PublicadorMetricas.class);

    private final CalculoMetricasCliente calculo;
    private final ApplicationEventPublisher eventos;
    private final RabbitTemplate rabbit;
    private final ObjectMapper json;

    public PublicadorMetricas(CalculoMetricasCliente calculo, ApplicationEventPublisher eventos,
                              RabbitTemplate rabbit, ObjectMapper json) {
        this.calculo = calculo;
        this.eventos = eventos;
        this.rabbit = rabbit;
        this.json = json;
    }

    /** Sin perfil vinculado no hay dónde guardar el segmento: la compra queda sin publicar métricas. */
    public void programar(String idEvento, String tipoEventoOrigen, Compra compra) {
        if (compra.getIdCliente() == null) {
            log.info("Compra {} sin perfil vinculado: no se publican métricas para la segmentación",
                    compra.getIdCompraOrigen());
            return;
        }
        eventos.publishEvent(calculo.calcular(idEvento, tipoEventoOrigen, compra.getIdCliente()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicar(MetricasCliente metricas) {
        try {
            Message mensaje = MessageBuilder.withBody(json.writeValueAsBytes(metricas))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setContentEncoding("UTF-8")
                    .setMessageId(metricas.idEvento())
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .build();
            rabbit.send(ConfiguracionRabbit.EXCHANGE_CRM, ConfiguracionRabbit.RUTA_METRICAS_ACTUALIZADAS, mensaje);
        } catch (JsonProcessingException | AmqpException ex) {
            // La compra ya quedó registrada; el segmento se corrige con el siguiente evento del cliente.
            log.error("No se pudieron publicar las métricas del cliente {} (evento {}): {}", metricas.idCliente(),
                    metricas.idEvento(), ex.getMessage());
        }
    }
}
