package com.maxiconecta.crm.fidelizacion.movimiento;

import com.maxiconecta.crm.fidelizacion.configuracion.ConfiguracionRabbit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Recibe de RabbitMQ los movimientos de puntos RIO-CRM-03 de Marketplace y Ventas (SCRUM-388). El
 * contenido se toma como texto para poder anotar en la bitácora incluso los mensajes mal formados.
 */
@Component
public class ReceptorMovimientos {

    private final IngestaMovimientos ingesta;

    public ReceptorMovimientos(IngestaMovimientos ingesta) {
        this.ingesta = ingesta;
    }

    @RabbitListener(queues = ConfiguracionRabbit.COLA_MOVIMIENTOS)
    public void recibir(Message mensaje) {
        ingesta.recibir(new String(mensaje.getBody(), StandardCharsets.UTF_8));
    }
}
