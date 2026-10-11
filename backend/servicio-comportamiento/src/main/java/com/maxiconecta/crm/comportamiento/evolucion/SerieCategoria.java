package com.maxiconecta.crm.comportamiento.evolucion;

import java.math.BigDecimal;
import java.util.List;

/**
 * Consumo de una categoría en cada periodo del rango, en el mismo orden que los periodos, con sus
 * totales en el rango.
 */
public record SerieCategoria(String categoria, BigDecimal monto, long compras, long unidades,
                             List<ConsumoPeriodo> consumo) {
}
