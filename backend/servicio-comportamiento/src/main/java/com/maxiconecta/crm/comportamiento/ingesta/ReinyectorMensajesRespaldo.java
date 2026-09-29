package com.maxiconecta.crm.comportamiento.ingesta;

import com.maxiconecta.crm.comportamiento.configuracion.ConfiguracionRabbit;
import com.rabbitmq.client.GetResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

/** Reinyecta un único mensaje de respaldo y lo confirma antes de retirarlo de la DLQ. */
@Service
public class ReinyectorMensajesRespaldo {

    private static final Logger log = LoggerFactory.getLogger(ReinyectorMensajesRespaldo.class);
    private static final long TIEMPO_CONFIRMACION_MS = 5_000;

    private final RabbitTemplate rabbit;

    public ReinyectorMensajesRespaldo(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    /** Reinyecta un mensaje de la cola de respaldo de compras. */
    public Optional<Reinyeccion> reinyectarUno(String usuario) {
        return reinyectarUno(ColaRespaldo.COMPRAS, usuario);
    }

    public Optional<Reinyeccion> reinyectarUno(ColaRespaldo cola, String usuario) {
        Reinyeccion resultado = rabbit.execute(channel -> {
            GetResponse mensaje = channel.basicGet(cola.respaldo(), false);
            if (mensaje == null) {
                return null;
            }

            long etiqueta = mensaje.getEnvelope().getDeliveryTag();
            try {
                // Comprueba que el destino existe y usa confirmaciones del broker antes del ack de la DLQ.
                channel.queueDeclarePassive(cola.destino());
                channel.confirmSelect();
                channel.basicPublish("", cola.destino(), false,
                        mensaje.getProps(), mensaje.getBody());
                channel.waitForConfirmsOrDie(TIEMPO_CONFIRMACION_MS);
                channel.basicAck(etiqueta, false);
                return new Reinyeccion(mensaje.getProps().getMessageId(), mensaje.getBody().length);
            } catch (IOException | InterruptedException | TimeoutException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                reponer(channel, etiqueta, ex);
                throw ex;
            }
        });
        if (resultado != null) {
            log.info("Mensaje de respaldo de {} reinyectado por {} (messageId={}, bytes={})", cola.destino(),
                    usuario != null && !usuario.isBlank() ? usuario : "usuario no informado",
                    resultado.idMensaje(), resultado.bytes());
        }
        return Optional.ofNullable(resultado);
    }

    private static void reponer(com.rabbitmq.client.Channel channel, long etiqueta, Exception original) {
        try {
            channel.basicNack(etiqueta, false, true);
        } catch (IOException ex) {
            original.addSuppressed(ex);
        }
    }

    public record Reinyeccion(String idMensaje, int bytes) {
    }

    /** Cola de respaldo (DLQ) y la cola a la que vuelven sus mensajes. */
    public enum ColaRespaldo {
        COMPRAS(ConfiguracionRabbit.COLA_COMPRAS_RESPALDO, ConfiguracionRabbit.COLA_COMPRAS),
        ANULACIONES(ConfiguracionRabbit.COLA_ANULACIONES_RESPALDO, ConfiguracionRabbit.COLA_ANULACIONES);

        private final String respaldo;
        private final String destino;

        ColaRespaldo(String respaldo, String destino) {
            this.respaldo = respaldo;
            this.destino = destino;
        }

        public String respaldo() {
            return respaldo;
        }

        public String destino() {
            return destino;
        }
    }
}
