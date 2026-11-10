package com.maxiconecta.crm.comportamiento.reporte;

import com.maxiconecta.crm.comportamiento.comparacion.TendenciaConsumo;
import com.maxiconecta.crm.comportamiento.comparacion.VariacionConsumo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Reporte comparativo de los indicadores de clientes entre dos periodos (SCRUM-62,
 * docs/compra/reporte-comparativo.md): los de cada periodo y su variación (criterio de aceptación 1).
 * Cada periodo es el reporte de comportamiento de compra de SCRUM-58, así que sus valores coinciden con él.
 *
 * <p>{@code sinDatos} es verdadero cuando ninguno de los dos periodos tiene compras vigentes.
 */
public record ReporteComparativo(OffsetDateTime generadoEn, Periodo periodoA, Periodo periodoB, String zonaHoraria,
                                 ReporteComportamiento.Alcance alcance, boolean sinDatos, String mensaje,
                                 List<Indicador> indicadores) {

    /** Fechas del periodo, ambas inclusive. */
    public record Periodo(LocalDate desde, LocalDate hasta) {
    }

    /**
     * Un indicador en los dos periodos y su variación de A a B ({@link VariacionConsumo}, la de SCRUM-40). Un
     * promedio puede ser nulo en un periodo sin compras: entonces no hay variación que calcular y los campos de
     * la variación y la tendencia quedan nulos.
     */
    public record Indicador(String codigo, String nombre, BigDecimal periodoA, BigDecimal periodoB,
                            BigDecimal variacionAbsoluta, BigDecimal variacionPorcentual, TendenciaConsumo tendencia) {

        static Indicador de(String codigo, String nombre, BigDecimal a, BigDecimal b) {
            if (a == null || b == null) {
                return new Indicador(codigo, nombre, a, b, null, null, null);
            }
            VariacionConsumo variacion = VariacionConsumo.calcular(a, b);
            return new Indicador(codigo, nombre, a, b, variacion.variacionAbsoluta(), variacion.variacionPorcentual(),
                    variacion.tendencia());
        }
    }
}
