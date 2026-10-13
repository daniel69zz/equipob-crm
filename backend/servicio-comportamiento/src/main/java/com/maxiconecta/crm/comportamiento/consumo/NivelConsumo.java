package com.maxiconecta.crm.comportamiento.consumo;

/**
 * Nivel de consumo de un cliente según su valor acumulado (docs/compra/nivel-consumo.md,
 * SCRUM-27). Un cliente sin compras vigentes no tiene nivel: es "sin nivel", no un valor de este
 * enum.
 */
public enum NivelConsumo {
    BAJO,
    MEDIO,
    ALTO
}
