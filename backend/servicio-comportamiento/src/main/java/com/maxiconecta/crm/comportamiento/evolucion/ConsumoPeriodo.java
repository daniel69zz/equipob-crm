package com.maxiconecta.crm.comportamiento.evolucion;

import java.math.BigDecimal;

/** Consumo vigente de una categoría en un periodo; un periodo sin compras va en cero. */
public record ConsumoPeriodo(String periodo, BigDecimal monto, long compras, long unidades) {

    static ConsumoPeriodo sinConsumo(String periodo) {
        return new ConsumoPeriodo(periodo, BigDecimal.ZERO, 0, 0);
    }
}
