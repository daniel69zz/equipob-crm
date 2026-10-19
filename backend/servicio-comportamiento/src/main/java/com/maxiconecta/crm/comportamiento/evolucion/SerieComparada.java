package com.maxiconecta.crm.comportamiento.evolucion;

import java.math.BigDecimal;
import java.util.List;

/** Evolución de una categoría con el detalle comparativo de cada periodo y su tendencia en el rango. */
public record SerieComparada(String categoria, BigDecimal monto, long compras, long unidades,
                             Tendencia tendencia, List<PuntoEvolucion> evolucion) {
}
