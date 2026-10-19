package com.maxiconecta.crm.segmentacion.consulta;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Segmento vigente del cliente y los demás clientes que lo comparten, sin incluirlo a él (SCRUM-378).
 * Sin segmento asignado, {@code sinDatos=true} y la lista vacía, como en {@link SegmentoResponse}.
 */
public record MiembrosSegmentoResponse(String segmento, OffsetDateTime asignadoEn, boolean sinDatos,
                                       List<Long> clientes) {

    public static MiembrosSegmentoResponse sinSegmento() {
        return new MiembrosSegmentoResponse(null, null, true, List.of());
    }
}
