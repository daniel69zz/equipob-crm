package com.maxiconecta.crm.comportamiento.periodo;

import com.maxiconecta.crm.comportamiento.compra.ResumenCompras;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Valor que consumió un cliente en un periodo (SCRUM-39, docs/compra/consumo-por-periodo.md): el
 * total vigente, el promedio por compra y cuántas compras lo componen. Un periodo sin compras
 * vigentes no es un error: el total y el promedio son 0, {@code sinDatos} es verdadero y
 * {@code mensaje} lo explica para que el usuario no lo confunda con "no se pudo calcular".
 */
public record ValorPorPeriodo(LocalDate desde, LocalDate hasta, String zonaHoraria, BigDecimal total,
                              BigDecimal promedio, long compras, boolean sinDatos, String mensaje) {

    private static final int DECIMALES = 2;

    public static ValorPorPeriodo de(RangoPeriodo rango, String zonaHoraria, ResumenCompras resumen) {
        if (resumen.compras() <= 0) {
            return new ValorPorPeriodo(rango.desde(), rango.hasta(), zonaHoraria, BigDecimal.ZERO, BigDecimal.ZERO,
                    0, true, "El cliente no tiene compras vigentes entre " + rango.desde() + " y " + rango.hasta()
                    + ".");
        }
        BigDecimal promedio = resumen.monto()
                .divide(BigDecimal.valueOf(resumen.compras()), DECIMALES, RoundingMode.HALF_UP);
        return new ValorPorPeriodo(rango.desde(), rango.hasta(), zonaHoraria, resumen.monto(), promedio,
                resumen.compras(), false, null);
    }
}
