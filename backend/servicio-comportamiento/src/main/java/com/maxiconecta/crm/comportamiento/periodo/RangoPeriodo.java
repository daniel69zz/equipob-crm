package com.maxiconecta.crm.comportamiento.periodo;

import com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Periodo de la consulta del valor consumido (SCRUM-346, docs/compra/consumo-por-periodo.md):
 * desde y hasta son fechas calendario, ambas inclusive, en la zona horaria del negocio. Un rango
 * incompleto, al revés o más largo que {@link #DIAS_MAXIMOS} se rechaza; un periodo sin compras
 * no es un error.
 */
public record RangoPeriodo(LocalDate desde, LocalDate hasta) {

    /** Cinco años: suficiente para analizar el aporte del cliente sin pedir un recorrido sin límite. */
    public static final long DIAS_MAXIMOS = 5 * 366L;

    public RangoPeriodo {
        if (desde == null) {
            throw new ReglaNegocioException("Falta la fecha 'desde' del periodo (formato AAAA-MM-DD)");
        }
        if (hasta == null) {
            throw new ReglaNegocioException("Falta la fecha 'hasta' del periodo (formato AAAA-MM-DD)");
        }
        if (desde.isAfter(hasta)) {
            throw new ReglaNegocioException("La fecha 'desde' (" + desde + ") no puede ser posterior a 'hasta' ("
                    + hasta + ")");
        }
        long dias = ChronoUnit.DAYS.between(desde, hasta) + 1;
        if (dias > DIAS_MAXIMOS) {
            throw new ReglaNegocioException("El periodo abarca " + dias + " días; el máximo es " + DIAS_MAXIMOS
                    + ". Acorte el rango");
        }
    }

    /** Primer instante del periodo: las 00:00 de {@code desde} en la zona del negocio (inclusive). */
    public OffsetDateTime inicio(ZoneId zona) {
        return desde.atStartOfDay(zona).toOffsetDateTime();
    }

    /** Primer instante posterior al periodo: las 00:00 del día siguiente a {@code hasta} (exclusivo). */
    public OffsetDateTime fin(ZoneId zona) {
        return hasta.plusDays(1).atStartOfDay(zona).toOffsetDateTime();
    }
}
