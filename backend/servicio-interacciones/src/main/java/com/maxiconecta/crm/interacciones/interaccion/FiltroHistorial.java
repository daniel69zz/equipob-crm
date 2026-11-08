package com.maxiconecta.crm.interacciones.interaccion;

import java.time.OffsetDateTime;

/**
 * Filtros del historial ya validados (SCRUM-431): tipo, texto en el asunto, la descripción o la referencia, y el
 * rango de fechas como instantes {@code [desde, hasta)}. Los nulos no filtran.
 */
public record FiltroHistorial(String tipo, String texto, OffsetDateTime desde, OffsetDateTime hasta,
                              int pagina, int tamanio) {
}
