package com.maxiconecta.crm.fidelizacion.caducidad;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Regla de caducidad de los puntos (SCRUM-418, docs/fidelizacion/caducidad-puntos.md): el saldo completo de un
 * cliente vence {@code vigenciaMeses} meses de calendario después de su última acumulación con puntos, en UTC.
 * Es la misma regla que informa la consulta de Marketplace y Ventas (RIO-CRM-04).
 */
public final class ReglaCaducidad {

    public static final int VIGENCIA_MINIMA = 1;
    public static final int VIGENCIA_MAXIMA = 120;

    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private ReglaCaducidad() {
    }

    /** Si el día no existe en el mes final, vence el último día de ese mes (31 de enero + 1 mes = fin de febrero). */
    public static OffsetDateTime fechaCaducidad(OffsetDateTime ultimaAcumulacion, int vigenciaMeses) {
        return ultimaAcumulacion.withOffsetSameInstant(ZoneOffset.UTC).plusMonths(vigenciaMeses);
    }

    /** El saldo está vencido desde el mismo instante de su fecha de caducidad. */
    public static boolean vencida(OffsetDateTime fechaCaducidad, OffsetDateTime momento) {
        return !fechaCaducidad.isAfter(momento);
    }
}
