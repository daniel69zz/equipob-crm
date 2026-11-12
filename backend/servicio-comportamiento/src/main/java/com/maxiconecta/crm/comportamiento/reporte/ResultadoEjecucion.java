package com.maxiconecta.crm.comportamiento.reporte;

/**
 * Resultado de una ejecución de un reporte programado (SCRUM-61). Solo una {@link #COMPLETADA} tiene archivo; una
 * {@link #FALLIDA} guarda la causa. Coincide con el CHECK de {@code comportamiento.ejecucion_reporte.estado} (V14).
 */
public enum ResultadoEjecucion {
    COMPLETADA, FALLIDA
}
