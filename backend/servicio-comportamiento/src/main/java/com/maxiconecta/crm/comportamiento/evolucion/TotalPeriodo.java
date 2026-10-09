package com.maxiconecta.crm.comportamiento.evolucion;

import java.math.BigDecimal;

/**
 * Monto de las categorías mostradas en un periodo y cuántas compras distintas las incluyen: una
 * compra con dos categorías cuenta una sola vez.
 */
public record TotalPeriodo(String periodo, BigDecimal monto, long compras) {
}
