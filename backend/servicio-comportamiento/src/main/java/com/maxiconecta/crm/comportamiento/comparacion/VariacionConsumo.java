package com.maxiconecta.crm.comportamiento.comparacion;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Variación de una métrica (frecuencia o valor de compra) del periodo A al periodo B
 * (docs/compra/comparacion-periodos.md, SCRUM-354): la variación absoluta es {@code b - a} y la
 * porcentual es {@code (b - a) / a * 100}, a 2 decimales. Un periodo en cero no es un error: si A es
 * 0 no hay base para un porcentaje, así que {@code variacionPorcentual} es nula y la tendencia es
 * {@code NUEVO} (o {@code ESTABLE} si B también es 0, que es una variación de 0%).
 */
public record VariacionConsumo(BigDecimal periodoA, BigDecimal periodoB, BigDecimal variacionAbsoluta,
                               BigDecimal variacionPorcentual, TendenciaConsumo tendencia) {

    private static final int DECIMALES = 2;
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    public static VariacionConsumo calcular(BigDecimal a, BigDecimal b) {
        BigDecimal absoluta = b.subtract(a);
        if (a.signum() == 0) {
            if (b.signum() == 0) {
                return new VariacionConsumo(a, b, absoluta, BigDecimal.ZERO.setScale(DECIMALES), TendenciaConsumo.ESTABLE);
            }
            return new VariacionConsumo(a, b, absoluta, null, TendenciaConsumo.NUEVO);
        }
        BigDecimal porcentual = absoluta.multiply(CIEN).divide(a, DECIMALES, RoundingMode.HALF_UP);
        TendenciaConsumo tendencia = absoluta.signum() > 0 ? TendenciaConsumo.CRECE
                : absoluta.signum() < 0 ? TendenciaConsumo.DECAE : TendenciaConsumo.ESTABLE;
        return new VariacionConsumo(a, b, absoluta, porcentual, tendencia);
    }

    public static VariacionConsumo calcular(long a, long b) {
        return calcular(BigDecimal.valueOf(a), BigDecimal.valueOf(b));
    }
}
