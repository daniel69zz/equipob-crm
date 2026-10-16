package com.maxiconecta.crm.comportamiento.preferencia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxiconecta.crm.comportamiento.configuracion.ConfiguracionRabbit;
import com.maxiconecta.crm.comportamiento.metrica.MetricasCliente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * Disparador de la actualización del perfil de preferencias (SCRUM-370,
 * docs/preferencias/actualizacion-por-eventos.md): escucha las métricas que este mismo servicio
 * publica tras cada compra confirmada o anulación ya registrada (SCRUM-26) y recalcula el perfil del
 * cliente, sin intervención manual.
 * <p>
 * Un mensaje inválido, de un cliente inexistente o consolidado, o con un consumo inconsistente va
 * directo a la cola de respaldo. Si el servicio de Perfil no responde, se reintenta y, agotados los
 * reintentos, también va a respaldo para su reproceso.
 */
@Component
public class ReceptorPreferencias {

    private static final Logger log = LoggerFactory.getLogger(ReceptorPreferencias.class);
    private static final Set<String> TIPOS = Set.of("COMPRA_CONFIRMADA", "COMPRA_ANULADA");
    private static final int LARGO_MAXIMO_ID_EVENTO = 64;

    private final PerfilesPreferencias perfiles;
    private final ObjectMapper json;

    public ReceptorPreferencias(PerfilesPreferencias perfiles, ObjectMapper json) {
        this.perfiles = perfiles;
        this.json = json;
    }

    @RabbitListener(queues = ConfiguracionRabbit.COLA_PREFERENCIAS)
    public void recibir(Message mensaje) {
        MetricasCliente metricas = leer(mensaje.getBody());
        try {
            PerfilesPreferencias.Actualizacion actualizacion = perfiles.actualizarPorEvento(metricas.idCliente(),
                    metricas.idEvento(), metricas.tipoEventoOrigen());
            log.info("Perfil de preferencias del cliente {} por el evento {} ({}): {}, versión {}",
                    metricas.idCliente(), metricas.idEvento(), metricas.tipoEventoOrigen(),
                    actualizacion.resultado(), actualizacion.perfil().version());
        } catch (PreferenciasException ex) {
            if (ex.estado() == HttpStatus.SERVICE_UNAVAILABLE) {
                throw ex;
            }
            throw new AmqpRejectAndDontRequeueException("No se actualizó el perfil de preferencias del cliente "
                    + metricas.idCliente() + " por el evento " + metricas.idEvento() + ": " + ex.getMessage(), ex);
        }
    }

    private MetricasCliente leer(byte[] contenido) {
        MetricasCliente metricas;
        try {
            metricas = json.readValue(contenido, MetricasCliente.class);
        } catch (IOException | RuntimeException ex) {
            throw new AmqpRejectAndDontRequeueException("El mensaje de métricas no es un JSON válido", ex);
        }
        if (metricas == null || metricas.idEvento() == null || metricas.idEvento().isBlank()
                || metricas.idEvento().length() > LARGO_MAXIMO_ID_EVENTO || metricas.idCliente() <= 0
                || !TIPOS.contains(metricas.tipoEventoOrigen())) {
            throw new AmqpRejectAndDontRequeueException(
                    "Mensaje de métricas incompleto: se requieren idEvento, idCliente y tipoEventoOrigen");
        }
        return metricas;
    }
}
