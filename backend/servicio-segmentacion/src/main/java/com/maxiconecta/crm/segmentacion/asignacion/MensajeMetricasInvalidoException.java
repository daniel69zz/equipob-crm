package com.maxiconecta.crm.segmentacion.asignacion;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;

/**
 * Mensaje de métricas que no se puede aplicar aunque se reintente (JSON inválido o datos
 * incompletos): no se reintenta y pasa directo a la cola de respaldo.
 */
public class MensajeMetricasInvalidoException extends AmqpRejectAndDontRequeueException {

    public MensajeMetricasInvalidoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
