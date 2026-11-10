package com.maxiconecta.crm.segmentacion.reporte;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Reporte de distribución de clientes por segmento, nivel de consumo y nivel de fidelización
 * (SCRUM-57, docs/segmentacion/reportes-distribucion.md). Cada corte trae la cantidad y el
 * porcentaje sobre {@code total}, que es la población del reporte después del filtro.
 *
 * <p>{@code sinDatos} es verdadero cuando el filtro no deja ningún cliente: el reporte se entrega
 * con 200 y los tres cortes vacíos, no con un error (criterio de aceptación 2).
 */
public record ReporteDistribucion(OffsetDateTime generadoEn, Filtro filtro, long total, boolean sinDatos,
                                  List<Grupo> segmentos, List<Grupo> nivelesConsumo,
                                  List<Grupo> nivelesFidelizacion, boolean fidelizacionDisponible) {

    /** Reporte sin clientes, cuando el filtro no deja ninguno. */
    static ReporteDistribucion sinDatos(OffsetDateTime generadoEn, FiltroReporte filtro) {
        return new ReporteDistribucion(generadoEn, Filtro.de(filtro), 0, true, List.of(), List.of(), List.of(), true);
    }

    /** Alcance con el que se generó, para que el reporte se pueda leer solo. */
    public record Filtro(String segmento, String nivelConsumo) {

        static Filtro de(FiltroReporte filtro) {
            return new Filtro(filtro.segmento(), filtro.nivelConsumoNombre());
        }
    }

    /**
     * Un grupo del corte. {@code valor} nulo es el grupo sin asignar: clientes sin segmento, sin
     * nivel de consumo calculado o sin nivel de fidelización.
     */
    public record Grupo(String valor, long clientes, BigDecimal porcentaje) {

        /** Porcentaje sobre el total con dos decimales; 0 % si el total es 0. */
        static Grupo de(String valor, long clientes, long total) {
            BigDecimal porcentaje = total <= 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(clientes).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
            return new Grupo(valor, clientes, porcentaje);
        }
    }
}
