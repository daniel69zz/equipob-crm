package com.maxiconecta.crm.comportamiento.riesgo;

/**
 * Nivel de riesgo de abandono de un cliente (SCRUM-35, docs/compra/riesgo-abandono.md). Un cliente
 * sin compras vigentes no tiene riesgo calculado: es "sin datos", no un valor de este enum.
 */
public enum NivelRiesgo {
    BAJO,
    MEDIO,
    ALTO
}
