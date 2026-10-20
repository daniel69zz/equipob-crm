package com.maxiconecta.crm.comportamiento.riesgo;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recalcula el riesgo de abandono de todos los clientes (SCRUM-322). El horario es configurable
 * ({@code comportamiento.riesgo-abandono.cron}); por defecto corre una vez al día, después de la
 * detección de cambios de comportamiento, para que una detección nueva y el paso de los días sin
 * comprar se reflejen en el riesgo (criterio de aceptación 2 de SCRUM-35).
 */
@Component
public class RiesgoAbandonoScheduler {

    private final CalculadoraRiesgoAbandono calculadora;

    public RiesgoAbandonoScheduler(CalculadoraRiesgoAbandono calculadora) {
        this.calculadora = calculadora;
    }

    @Scheduled(cron = "${comportamiento.riesgo-abandono.cron:0 30 4 * * *}")
    public void ejecutar() {
        calculadora.recalcularTodos();
    }
}
