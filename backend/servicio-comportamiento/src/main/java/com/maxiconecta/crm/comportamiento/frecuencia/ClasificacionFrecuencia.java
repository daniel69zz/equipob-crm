package com.maxiconecta.crm.comportamiento.frecuencia;

/**
 * Clasificación de un cliente según su frecuencia de compra (docs/compra/clasificacion-frecuencia.md,
 * SCRUM-38). Un cliente sin compras vigentes no tiene clasificación: es "sin clasificación", no un
 * valor de este enum.
 */
public enum ClasificacionFrecuencia {
    OCASIONAL,
    HABITUAL,
    FRECUENTE
}
