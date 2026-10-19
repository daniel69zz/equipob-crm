package com.maxiconecta.crm.comportamiento.cambio;

/**
 * Resultado de la última evaluación de un cliente (docs/compra/cambios-comportamiento.md):
 * {@code SIN_LINEA_BASE} cuando su historial es insuficiente (no genera detecciones, criterio de
 * aceptación 2 de SCRUM-34), {@code ESTABLE} cuando nada supera el umbral y {@code CON_VARIACION}
 * cuando la frecuencia o el monto lo superan.
 */
public enum EstadoComportamiento {
    SIN_LINEA_BASE,
    ESTABLE,
    CON_VARIACION
}
