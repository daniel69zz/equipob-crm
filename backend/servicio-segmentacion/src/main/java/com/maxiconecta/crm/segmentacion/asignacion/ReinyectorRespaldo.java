package com.maxiconecta.crm.segmentacion.asignacion;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

/**
 * Devuelve un mensaje de la cola de respaldo de métricas a la cola principal para reprocesarlo
 * (SCRUM-241). Solo lo retira del respaldo después de que el broker confirma la publicación, como
 * el reinyector de servicio-comportamiento.
 */
@Service
public class ReinyectorRespaldo {

    private static final Logger log = LoggerFactory.getLogger(ReinyectorRespaldo.class);
    private static final long TIEMPO_CONFIRMACION_MS = 5_000;

    private final RabbitTemplate rabbit;

    public ReinyectorRespaldo(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    public Optional<Reinyeccion> reinyectarUno(String usuario) {
        Reinyeccion resultado = rabbit.execute(canal -> {
            GetResponse mensaje = canal.basicGet(ConfiguracionRabbit.COLA_METRICAS_RESPALDO, false);
            if (mensaje == null) {
                return null;
            }
            long etiqueta = mensaje.getEnvelope().getDeliveryTag();
            try {
                canal.queueDeclarePassive(ConfiguracionRabbit.COLA_METRICAS);
                canal.confirmSelect();
                canal.basicPublish("", ConfiguracionRabbit.COLA_METRICAS, false, mensaje.getProps(), mensaje.getBody());
                canal.waitForConfirmsOrDie(TIEMPO_CONFIRMACION_MS);
                canal.basicAck(etiqueta, false);
                return new Reinyeccion(mensaje.getProps().getMessageId(), mensaje.getBody().length);
            } catch (IOException | InterruptedException | TimeoutException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                reponer(canal, etiqueta, ex);
                throw ex;
            }
        });
        if (resultado != null) {
            log.info("Mensaje de métricas reinyectado por {} (messageId={}, bytes={})", usuario,
                    resultado.idMensaje(), resultado.bytes());
        }
        return Optional.ofNullable(resultado);
    }

    private static void reponer(Channel canal, long etiqueta, Exception original) {
        try {
            canal.basicNack(etiqueta, false, true);
        } catch (IOException ex) {
            original.addSuppressed(ex);
        }
    }

    public record Reinyeccion(String idMensaje, int bytes) {
    }
}
