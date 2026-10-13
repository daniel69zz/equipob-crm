package com.maxiconecta.crm.comportamiento.evolucion;

import java.math.BigDecimal;

/**
 * Consumo de una categoría en un periodo, comparado con el periodo anterior (nulo en el primero)
 * y con el periodo base.
 */
public record PuntoEvolucion(String periodo, BigDecimal monto, long compras, long unidades,
                             Variacion variacionAnterior, Variacion variacionBase) {
}
