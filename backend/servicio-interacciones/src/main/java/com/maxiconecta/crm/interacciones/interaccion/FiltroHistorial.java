package com.maxiconecta.crm.interacciones.interaccion;

import java.time.OffsetDateTime;

/**
 * Filtros del historial ya validados (SCRUM-431): tipo, estado de seguimiento (SCRUM-55), texto en el asunto, la
 * descripción o la referencia, el rango de fechas como instantes {@code [desde, hasta)} y el agente responsable
 * (SCRUM-56). Los nulos no filtran.
 */
public record FiltroHistorial(String tipo, String estado, String texto, OffsetDateTime desde, OffsetDateTime hasta,
                              int pagina, int tamanio, String responsable) {

    public FiltroHistorial(String tipo, String estado, String texto, OffsetDateTime desde, OffsetDateTime hasta,
                           int pagina, int tamanio) {
        this(tipo, estado, texto, desde, hasta, pagina, tamanio, null);
    }
}
