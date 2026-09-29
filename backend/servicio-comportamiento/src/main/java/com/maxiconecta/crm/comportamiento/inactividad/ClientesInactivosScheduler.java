package com.maxiconecta.crm.comportamiento.inactividad;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ejecuta la identificación de clientes inactivos de forma automática (SCRUM-297). El horario es
 * configurable (`comportamiento.inactividad.cron`); por defecto corre una vez al día, de madrugada.
 */
@Component
public class ClientesInactivosScheduler {

    private final DetectorClientesInactivos detector;

    public ClientesInactivosScheduler(DetectorClientesInactivos detector) {
        this.detector = detector;
    }

    @Scheduled(cron = "${comportamiento.inactividad.cron:0 0 3 * * *}")
    public void ejecutar() {
        detector.detectar();
    }
}
