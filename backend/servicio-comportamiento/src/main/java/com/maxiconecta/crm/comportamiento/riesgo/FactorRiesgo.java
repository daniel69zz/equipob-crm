package com.maxiconecta.crm.comportamiento.riesgo;

/**
 * Factores que suman puntos al riesgo de abandono (SCRUM-324, docs/compra/riesgo-abandono.md): son
 * los que explican el nivel en la ficha del cliente (criterio de aceptación 4 de SCRUM-35).
 */
public enum FactorRiesgo {
    RECENCIA,
    FRECUENCIA,
    VALOR,
    CAMBIO_COMPORTAMIENTO
}
