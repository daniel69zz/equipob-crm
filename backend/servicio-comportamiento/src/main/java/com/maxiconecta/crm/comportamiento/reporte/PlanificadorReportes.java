package com.maxiconecta.crm.comportamiento.reporte;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Revisa cada minuto (configurable) los reportes programados vencidos (SCRUM-61). Se desactiva con
 * {@code comportamiento.reportes.programacion.habilitada=false}, como en las pruebas.
 */
@Component
@ConditionalOnProperty(name = "comportamiento.reportes.programacion.habilitada", havingValue = "true", matchIfMissing = true)
public class PlanificadorReportes {

    private final ProgramacionReportes servicio;

    public PlanificadorReportes(ProgramacionReportes servicio) {
        this.servicio = servicio;
    }

    @Scheduled(fixedDelayString = "${comportamiento.reportes.programacion.intervalo-ms:60000}",
            initialDelayString = "${comportamiento.reportes.programacion.intervalo-ms:60000}")
    public void ejecutar() {
        servicio.ejecutarPendientes();
    }
}
