package com.maxiconecta.crm.segmentacion.consulta;

import com.maxiconecta.crm.segmentacion.segmento.SegmentoCliente;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Segmento del cliente, o {@code sinDatos=true} si todavía no tiene uno asignado (CA2 de
 * SCRUM-10: la ficha integral muestra ese bloque vacío con una leyenda, sin que sea un error).
 */
public record SegmentoResponse(String segmento, OffsetDateTime asignadoEn, boolean sinDatos) {

    public static SegmentoResponse de(Optional<SegmentoCliente> segmento) {
        return segmento.map(s -> new SegmentoResponse(s.getSegmento(), s.getAsignadoEn(), false))
                .orElseGet(() -> new SegmentoResponse(null, null, true));
    }
}
