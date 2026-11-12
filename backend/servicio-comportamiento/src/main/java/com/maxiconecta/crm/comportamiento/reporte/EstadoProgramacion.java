package com.maxiconecta.crm.comportamiento.reporte;

/**
 * Estado de un reporte programado (SCRUM-61). Solo las {@link #ACTIVA} se ejecutan; una {@link #CANCELADA} ya no
 * se edita ni se reactiva. Coincide con el CHECK de {@code comportamiento.reporte_programado.estado} (V14).
 */
public enum EstadoProgramacion {
    ACTIVA, PAUSADA, CANCELADA
}
