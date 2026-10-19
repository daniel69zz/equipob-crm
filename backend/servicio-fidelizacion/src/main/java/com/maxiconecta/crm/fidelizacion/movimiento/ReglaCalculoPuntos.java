package com.maxiconecta.crm.fidelizacion.movimiento;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Regla de cálculo de puntos (SCRUM-386, docs/fidelizacion/acumulacion-puntos.md): un punto por cada
 * tramo completo de {@code bolivianosPorPunto} consumidos, redondeando hacia abajo y sin tope. Un
 * valor menor que un tramo genera 0 puntos.
 */
@Component
public class ReglaCalculoPuntos {

    private final BigDecimal bolivianosPorPunto;

    public ReglaCalculoPuntos(@Value("${fidelizacion.puntos.bolivianos-por-punto:10}") BigDecimal bolivianosPorPunto) {
        if (bolivianosPorPunto == null || bolivianosPorPunto.signum() <= 0) {
            throw new IllegalStateException("La regla de puntos no es válida: los bolivianos por punto deben ser "
                    + "mayores que cero (" + bolivianosPorPunto + ")");
        }
        this.bolivianosPorPunto = bolivianosPorPunto;
    }

    public BigDecimal bolivianosPorPunto() {
        return bolivianosPorPunto;
    }

    public long puntos(BigDecimal valorConsumido) {
        if (valorConsumido == null || valorConsumido.signum() <= 0) {
            throw new IllegalArgumentException("El valor consumido debe ser mayor que cero");
        }
        return valorConsumido.divide(bolivianosPorPunto, 0, RoundingMode.DOWN).longValueExact();
    }
}
