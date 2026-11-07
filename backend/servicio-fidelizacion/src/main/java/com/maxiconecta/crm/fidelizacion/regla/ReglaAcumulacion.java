package com.maxiconecta.crm.fidelizacion.regla;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

/**
 * Una versión de la regla de acumulación de puntos (SCRUM-46, docs/fidelizacion/reglas-acumulacion.md).
 * <p>
 * Lógica de cálculo (SCRUM-394): una compra de menos de {@code montoMinimo} bolivianos no genera puntos;
 * desde el monto mínimo (incluido), se otorga un punto por cada tramo completo de
 * {@code bolivianosPorPunto}, redondeando hacia abajo y sin tope.
 */
public record ReglaAcumulacion(long version, BigDecimal bolivianosPorPunto, BigDecimal montoMinimo, String motivo,
                               String modificadoPor, OffsetDateTime vigenteDesde) {

    public ReglaAcumulacion {
        if (bolivianosPorPunto == null || bolivianosPorPunto.signum() <= 0) {
            throw new IllegalArgumentException("Los bolivianos por punto deben ser mayores que cero");
        }
        if (montoMinimo == null || montoMinimo.signum() < 0) {
            throw new IllegalArgumentException("El monto mínimo de compra no puede ser negativo");
        }
    }

    public long puntos(BigDecimal valorConsumido) {
        if (valorConsumido == null || valorConsumido.signum() <= 0) {
            throw new IllegalArgumentException("El valor consumido debe ser mayor que cero");
        }
        if (valorConsumido.compareTo(montoMinimo) < 0) {
            return 0;
        }
        return valorConsumido.divide(bolivianosPorPunto, 0, RoundingMode.DOWN).longValueExact();
    }

    /** Misma tasa y mismo monto mínimo, sin importar la escala de los decimales. */
    public boolean mismosValores(BigDecimal bolivianosPorPunto, BigDecimal montoMinimo) {
        return this.bolivianosPorPunto.compareTo(bolivianosPorPunto) == 0 && this.montoMinimo.compareTo(montoMinimo) == 0;
    }
}
