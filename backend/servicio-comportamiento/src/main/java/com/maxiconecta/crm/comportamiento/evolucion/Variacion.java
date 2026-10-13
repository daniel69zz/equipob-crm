package com.maxiconecta.crm.comportamiento.evolucion;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Diferencia del monto de un periodo respecto de otro. Sin monto de referencia no hay base para
 * un porcentaje: {@code porcentaje} es nulo (docs/compra/evolucion-consumo-categoria.md).
 */
public record Variacion(BigDecimal monto, BigDecimal porcentaje) {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    static Variacion entre(BigDecimal referencia, BigDecimal actual) {
        BigDecimal diferencia = actual.subtract(referencia);
        BigDecimal porcentaje = referencia.signum() == 0
                ? null
                : diferencia.multiply(CIEN).divide(referencia, 2, RoundingMode.HALF_UP);
        return new Variacion(diferencia, porcentaje);
    }
}
