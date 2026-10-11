package com.maxiconecta.crm.comportamiento.evolucion;

import java.time.LocalDate;
import java.util.List;

/**
 * Evolución del consumo de un cliente por categoría en un rango (SCRUM-32,
 * docs/compra/evolucion-consumo-categoria.md#consulta). Todos los periodos del rango aparecen,
 * con o sin compras; {@code sinDatos} avisa que no hubo consumo vigente en todo el rango.
 */
public record EvolucionConsumo(LocalDate desde, LocalDate hasta, TipoPeriodo periodo, String zonaHoraria,
                               String periodoBase, List<Periodo> periodos, List<String> categoriasDisponibles,
                               List<SerieComparada> categorias, List<TotalPeriodo> totales,
                               CalidadDatos calidadDatos, boolean sinDatos) {
}
