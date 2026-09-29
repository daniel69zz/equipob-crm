package com.maxiconecta.crm.comportamiento.ingesta;

import com.maxiconecta.crm.comportamiento.configuracion.ConfiguracionRabbit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Recibe de RabbitMQ las anulaciones y devoluciones de compra de Marketplace y Ventas (RIO-CRM-05).
 * El contenido se toma como texto para poder anotar en la bitácora incluso los mensajes mal formados.
 */
@Component
public class ReceptorAnulaciones {

    private final IngestaCompras ingesta;

    public ReceptorAnulaciones(IngestaCompras ingesta) {
        this.ingesta = ingesta;
    }

    @RabbitListener(queues = ConfiguracionRabbit.COLA_ANULACIONES)
    public void recibir(Message mensaje) {
        ingesta.recibirAnulacion(new String(mensaje.getBody(), StandardCharsets.UTF_8));
    }
}
