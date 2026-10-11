package com.maxiconecta.crm.comportamiento.comparacion;

import com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException;
import com.maxiconecta.crm.comportamiento.periodo.RangoPeriodo;

import java.time.LocalDate;

/**
 * Los dos periodos de una comparación (docs/compra/comparacion-periodos.md, SCRUM-354). Cada uno
 * cumple las validaciones de {@link RangoPeriodo} (completo, no al revés, de hasta 5 años) y no
 * pueden superponerse: una compra que entrara en los dos periodos contaría dos veces y la variación
 * no diría nada. El periodo A es la base y el B el comparado; no hace falta que A sea anterior a B.
 */
public record PeriodosAComparar(RangoPeriodo a, RangoPeriodo b) {

    public PeriodosAComparar {
        if (a.desde().compareTo(b.hasta()) <= 0 && b.desde().compareTo(a.hasta()) <= 0) {
            throw new ReglaNegocioException("Los periodos a comparar no pueden superponerse: " + a.desde() + " a "
                    + a.hasta() + " y " + b.desde() + " a " + b.hasta());
        }
    }

    public static PeriodosAComparar de(LocalDate desdeA, LocalDate hastaA, LocalDate desdeB, LocalDate hastaB) {
        return new PeriodosAComparar(periodo("A", desdeA, hastaA), periodo("B", desdeB, hastaB));
    }

    private static RangoPeriodo periodo(String nombre, LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new ReglaNegocioException("Falta una fecha del periodo " + nombre + ": se necesitan 'desde"
                    + nombre + "' y 'hasta" + nombre + "' (formato AAAA-MM-DD)");
        }
        try {
            return new RangoPeriodo(desde, hasta);
        } catch (ReglaNegocioException ex) {
            throw new ReglaNegocioException("Periodo " + nombre + ": " + ex.getMessage());
        }
    }
}
