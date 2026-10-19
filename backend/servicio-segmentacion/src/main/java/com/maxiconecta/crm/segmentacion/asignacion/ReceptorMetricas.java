package com.maxiconecta.crm.segmentacion.asignacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Recibe las métricas que publica servicio-comportamiento tras cada compra o anulación y reevalúa el
 * segmento del cliente (SCRUM-261, docs/segmentacion/asignacion-automatica.md).
 * <p>
 * Un mensaje inválido va directo a la cola de respaldo; uno que falla por un error transitorio (la
 * base no responde) se reintenta y, agotados los reintentos, también va a respaldo.
 */
@Component
public class ReceptorMetricas {

    private static final Logger log = LoggerFactory.getLogger(ReceptorMetricas.class);

    private final AsignacionSegmento asignacion;
    private final ObjectMapper json;

    public ReceptorMetricas(AsignacionSegmento asignacion, ObjectMapper json) {
        this.asignacion = asignacion;
        this.json = json;
    }

    @RabbitListener(queues = ConfiguracionRabbit.COLA_METRICAS)
    public void recibir(Message mensaje) {
        procesar(mensaje.getBody());
    }

    /** Mismo tratamiento para RabbitMQ y para Pub/Sub (docs/mensajeria/pubsub.md). */
    public void procesar(byte[] contenido) {
        MetricasCliente metricas = leer(contenido);
        AsignacionSegmento.Resultado resultado = asignacion.aplicar(metricas);
        log.debug("Métricas del evento {} para el cliente {}: {}", metricas.idEvento(), metricas.idCliente(), resultado);
    }

    private MetricasCliente leer(byte[] contenido) {
        MetricasCliente metricas;
        try {
            metricas = json.readValue(contenido, MetricasCliente.class);
        } catch (IOException | RuntimeException ex) {
            throw new MensajeMetricasInvalidoException("El mensaje de métricas no es un JSON válido", ex);
        }
        if (metricas == null) {
            throw new MensajeMetricasInvalidoException("El mensaje de métricas está vacío", null);
        }
        String problema = metricas.problema();
        if (!problema.isEmpty()) {
            throw new MensajeMetricasInvalidoException("Mensaje de métricas incompleto: " + problema, null);
        }
        return metricas;
    }
}
