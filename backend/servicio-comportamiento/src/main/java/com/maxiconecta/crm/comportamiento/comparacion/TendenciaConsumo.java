package com.maxiconecta.crm.comportamiento.comparacion;

/**
 * Cómo cambió una métrica entre dos periodos (docs/compra/comparacion-periodos.md, SCRUM-354).
 * {@code NUEVO} es el caso en que el periodo base no tuvo nada y el comparado sí: no hay base sobre
 * la cual calcular un porcentaje.
 */
public enum TendenciaConsumo {
    CRECE,
    DECAE,
    ESTABLE,
    NUEVO
}
