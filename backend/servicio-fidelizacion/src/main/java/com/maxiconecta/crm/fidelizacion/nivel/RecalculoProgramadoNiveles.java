package com.maxiconecta.crm.fidelizacion.nivel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recalcula cada noche el nivel de todos los clientes (SCRUM-411). Hace falta aunque su saldo no cambie:
 * con el paso del tiempo, las compras antiguas salen del periodo de evaluación y el cliente puede bajar de
 * nivel.
 */
@Component
public class RecalculoProgramadoNiveles {

    private static final Logger log = LoggerFactory.getLogger(RecalculoProgramadoNiveles.class);

    private final ServicioNiveles servicio;

    public RecalculoProgramadoNiveles(ServicioNiveles servicio) {
        this.servicio = servicio;
    }

    @Scheduled(cron = "${fidelizacion.niveles.recalculo-cron:0 30 2 * * *}",
            zone = "${fidelizacion.niveles.zona-horaria:America/La_Paz}")
    public void recalcular() {
        try {
            int cambiaron = servicio.recalcularTodos();
            log.info("Recálculo programado de niveles: {} clientes cambiaron de nivel", cambiaron);
        } catch (RuntimeException ex) {
            log.warn("No se pudo recalcular los niveles; se reintentará en el próximo ciclo: {}", ex.getMessage());
        }
    }
}
