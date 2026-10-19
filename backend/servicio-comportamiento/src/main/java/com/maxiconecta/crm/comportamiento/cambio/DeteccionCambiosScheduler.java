package com.maxiconecta.crm.comportamiento.cambio;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ejecuta la detección de cambios de comportamiento de todos los clientes (SCRUM-34). El horario es
 * configurable ({@code comportamiento.cambio-comportamiento.cron}); por defecto corre una vez al día,
 * de madrugada, después de la detección de inactivos y de la clasificación por nivel de consumo.
 * Corre de forma periódica, no con cada compra, porque una disminución se manifiesta justamente por
 * la ausencia de compras.
 */
@Component
public class DeteccionCambiosScheduler {

    private final DetectorCambiosComportamiento detector;

    public DeteccionCambiosScheduler(DetectorCambiosComportamiento detector) {
        this.detector = detector;
    }

    @Scheduled(cron = "${comportamiento.cambio-comportamiento.cron:0 0 4 * * *}")
    public void ejecutar() {
        detector.detectarTodos();
    }
}
