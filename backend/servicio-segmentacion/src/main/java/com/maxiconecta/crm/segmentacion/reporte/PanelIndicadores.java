package com.maxiconecta.crm.segmentacion.reporte;

import com.maxiconecta.crm.segmentacion.reporte.ReporteDistribucion.Grupo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Panel con los principales indicadores de clientes (SCRUM-59, docs/segmentacion/panel-indicadores.md):
 * clientes activos, distribución por segmento y puntos acumulados (criterio de aceptación 1). Se calcula al
 * pedirlo, así que al recargarlo refleja los cambios (criterio 2).
 *
 * <p>{@code clientes} es la población de la segmentación, la misma del reporte de distribución (SCRUM-57):
 * los clientes con métricas de compra o con segmento. {@code sinDatos} es verdadero cuando no hay ninguno.
 */
public record PanelIndicadores(OffsetDateTime generadoEn, long clientes, boolean sinDatos, Activos activos,
                               List<Grupo> segmentos, Puntos puntos) {

    /**
     * Clientes activos (SCRUM-475). Si servicio-comportamiento no respondió, {@code disponible} es falso y
     * los demás campos nulos.
     */
    public record Activos(boolean disponible, Long activos, BigDecimal porcentaje, Integer umbralDias) {

        static Activos de(IndicadoresExternos.ClientesActivos activos, long clientes) {
            BigDecimal porcentaje = clientes <= 0 ? BigDecimal.ZERO.setScale(2)
                    : BigDecimal.valueOf(activos.activos()).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(clientes), 2, RoundingMode.HALF_UP);
            return new Activos(true, activos.activos(), porcentaje, activos.umbralDias());
        }

        static Activos noDisponible() {
            return new Activos(false, null, null, null);
        }
    }

    /** Puntos acumulados (SCRUM-476). Si servicio-fidelizacion no respondió, {@code disponible} es falso. */
    public record Puntos(boolean disponible, Long puntos, Long clientesConPuntos) {

        static Puntos de(IndicadoresExternos.PuntosAcumulados puntos) {
            return new Puntos(true, puntos.puntos(), puntos.clientesConPuntos());
        }

        static Puntos noDisponible() {
            return new Puntos(false, null, null);
        }
    }
}
