package com.maxiconecta.crm.comportamiento.evolucion;

/**
 * Resumen de cómo cambió el consumo de una categoría entre la primera y la segunda mitad del
 * rango (docs/compra/evolucion-consumo-categoria.md#tendencia-de-cada-categoría).
 */
public enum Tendencia {
    /** El rango tiene un solo periodo. */
    SIN_COMPARACION,
    /** Sin consumo en todo el rango. */
    SIN_CONSUMO,
    /** Hubo consumo, pero ninguno en la segunda mitad. */
    DEJO_DE_COMPRAR,
    CRECE,
    DECRECE,
    ESTABLE
}
