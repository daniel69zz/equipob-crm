package com.maxiconecta.crm.comportamiento.evolucion;

import com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Tamaño de los periodos en que se agrupa la evolución del consumo, con la clave con la que se
 * identifica cada uno (docs/compra/evolucion-consumo-categoria.md#periodos).
 */
public enum TipoPeriodo {

    MENSUAL {
        @Override
        LocalDate inicioDe(LocalDate dia) {
            return dia.withDayOfMonth(1);
        }

        @Override
        LocalDate siguiente(LocalDate inicio) {
            return inicio.plusMonths(1);
        }

        @Override
        String clave(LocalDate inicio) {
            return YearMonth.from(inicio).toString();
        }

        @Override
        long indice(LocalDate dia) {
            return dia.getYear() * 12L + dia.getMonthValue() - 1;
        }
    },

    TRIMESTRAL {
        @Override
        LocalDate inicioDe(LocalDate dia) {
            return LocalDate.of(dia.getYear(), (trimestre(dia) - 1) * 3 + 1, 1);
        }

        @Override
        LocalDate siguiente(LocalDate inicio) {
            return inicio.plusMonths(3);
        }

        @Override
        String clave(LocalDate inicio) {
            return inicio.getYear() + "-T" + trimestre(inicio);
        }

        @Override
        long indice(LocalDate dia) {
            return dia.getYear() * 4L + trimestre(dia) - 1;
        }

        private int trimestre(LocalDate dia) {
            return (dia.getMonthValue() - 1) / 3 + 1;
        }
    },

    ANUAL {
        @Override
        LocalDate inicioDe(LocalDate dia) {
            return dia.withDayOfYear(1);
        }

        @Override
        LocalDate siguiente(LocalDate inicio) {
            return inicio.plusYears(1);
        }

        @Override
        String clave(LocalDate inicio) {
            return String.valueOf(inicio.getYear());
        }

        @Override
        long indice(LocalDate dia) {
            return dia.getYear();
        }
    };

    /** Primer día del periodo que contiene {@code dia}. */
    abstract LocalDate inicioDe(LocalDate dia);

    /** Primer día del periodo siguiente al que empieza en {@code inicio}. */
    abstract LocalDate siguiente(LocalDate inicio);

    /** Clave del periodo que empieza en {@code inicio}: {@code 2026-09}, {@code 2026-T3} o {@code 2026}. */
    abstract String clave(LocalDate inicio);

    /** Posición absoluta del periodo de {@code dia}: dos días del mismo periodo tienen el mismo índice. */
    abstract long indice(LocalDate dia);

    /** Cuántos periodos de este tipo tocan el rango, sin construirlos. */
    long cantidadEntre(LocalDate desde, LocalDate hasta) {
        return indice(hasta) - indice(desde) + 1;
    }

    /** Sin valor, el periodo es mensual. No distingue mayúsculas ni espacios a los lados. */
    public static TipoPeriodo parsear(String texto) {
        if (texto == null || texto.isBlank()) {
            return MENSUAL;
        }
        try {
            return valueOf(texto.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Periodo desconocido: '" + texto + "' (se espera MENSUAL, TRIMESTRAL o ANUAL)");
        }
    }
}
