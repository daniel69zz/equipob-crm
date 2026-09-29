package com.maxiconecta.crm.comportamiento.compra;

import java.math.BigDecimal;

/**
 * Compras vigentes de un cliente y el monto vigente que suman. Es la base común de los
 * indicadores que se calculan sobre el historial (ticket promedio, valor acumulado).
 */
public record ResumenCompras(long compras, BigDecimal monto) {

    public static final ResumenCompras VACIO = new ResumenCompras(0, BigDecimal.ZERO);
}
