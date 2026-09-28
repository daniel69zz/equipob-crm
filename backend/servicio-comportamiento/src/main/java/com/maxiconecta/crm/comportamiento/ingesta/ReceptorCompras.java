package com.maxiconecta.crm.comportamiento.ingesta;

import com.maxiconecta.crm.comportamiento.configuracion.ConfiguracionRabbit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Recibe de RabbitMQ los eventos de compra confirmada de Marketplace y Ventas.
 * El contenido se toma como texto para poder anotar en la bitácora incluso los mensajes mal formados.
 */
@Component
public class ReceptorCompras {

    private final IngestaCompras ingesta;

    public ReceptorCompras(IngestaCompras ingesta) {
        this.ingesta = ingesta;
    }

    @RabbitListener(queues = ConfiguracionRabbit.COLA_COMPRAS)
    public void recibir(Message mensaje) {
        ingesta.recibir(new String(mensaje.getBody(), StandardCharsets.UTF_8));
    }
}
