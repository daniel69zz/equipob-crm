package com.maxiconecta.crm.comportamiento.indicador;

import com.maxiconecta.crm.comportamiento.compra.ResumenCompras;

import java.math.BigDecimal;

/**
 * Valor acumulado que consumió un cliente: la suma de lo vigente de sus compras
 * (docs/compra/valor-acumulado.md, SCRUM-33). A diferencia del ticket promedio, una suma sí está
 * definida sin compras vigentes (es 0, no una división entre cero); {@code sinDatos} solo avisa
 * que el cliente no tiene compras vigentes que sumar, sin volver el 0 ambiguo con "no consultado".
 */
public record ValorAcumulado(BigDecimal valor, long compras, boolean sinDatos) {

    public static ValorAcumulado de(ResumenCompras resumen) {
        if (resumen.compras() <= 0) {
            return new ValorAcumulado(BigDecimal.ZERO, 0, true);
        }
        return new ValorAcumulado(resumen.monto(), resumen.compras(), false);
    }
}
