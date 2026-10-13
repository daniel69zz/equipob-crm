package com.maxiconecta.crm.comportamiento.consumo;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ejecuta la clasificación de todos los clientes por nivel de consumo de forma automática
 * (SCRUM-268). El horario es configurable (`comportamiento.nivel-consumo.cron`); por defecto corre
 * una vez al día, de madrugada, después de la detección de clientes inactivos.
 */
@Component
public class ClasificacionConsumoScheduler {

    private final ClasificadorConsumo clasificador;

    public ClasificacionConsumoScheduler(ClasificadorConsumo clasificador) {
        this.clasificador = clasificador;
    }

    @Scheduled(cron = "${comportamiento.nivel-consumo.cron:0 30 3 * * *}")
    public void ejecutar() {
        clasificador.clasificarTodos();
    }
}
