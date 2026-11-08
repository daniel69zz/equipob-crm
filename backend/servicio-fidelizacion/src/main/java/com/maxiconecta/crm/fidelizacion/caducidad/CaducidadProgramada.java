package com.maxiconecta.crm.fidelizacion.caducidad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Proceso automático de caducidad de puntos (SCRUM-415): corre cada día a las 02:00 de La Paz, antes del
 * recálculo de niveles (02:30). El horario se cambia con {@code FIDELIZACION_CADUCIDAD_CRON}.
 */
@Component
public class CaducidadProgramada {

    private static final Logger log = LoggerFactory.getLogger(CaducidadProgramada.class);

    private final ProcesoCaducidad proceso;

    public CaducidadProgramada(ProcesoCaducidad proceso) {
        this.proceso = proceso;
    }

    @Scheduled(cron = "${fidelizacion.caducidad.cron:0 0 2 * * *}",
            zone = "${fidelizacion.caducidad.zona-horaria:America/La_Paz}")
    public void caducar() {
        try {
            proceso.caducarVencidos();
        } catch (RuntimeException ex) {
            log.warn("No se pudo ejecutar la caducidad de puntos; se reintentará en el próximo ciclo: {}", ex.getMessage());
        }
    }
}
