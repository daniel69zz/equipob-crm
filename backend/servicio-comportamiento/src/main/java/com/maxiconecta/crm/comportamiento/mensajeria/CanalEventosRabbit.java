package com.maxiconecta.crm.comportamiento.mensajeria;

import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Publicación con RabbitMQ, el broker predeterminado. */
@Component
@ConditionalOnProperty(name = Mensajeria.PROPIEDAD_BROKER, havingValue = Mensajeria.RABBIT, matchIfMissing = true)
public class CanalEventosRabbit implements CanalEventos {

    private final RabbitTemplate rabbit;

    public CanalEventosRabbit(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @Override
    public void publicar(String destino, String ruta, byte[] cuerpo, String idMensaje) {
        rabbit.send(destino, ruta, MessageBuilder.withBody(cuerpo)
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding("UTF-8")
                .setMessageId(idMensaje)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build());
    }
}
