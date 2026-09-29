package com.maxiconecta.crm.comportamiento.indicador;

import com.maxiconecta.crm.comportamiento.compra.ResumenCompras;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Ticket promedio de un cliente: monto vigente acumulado dividido entre sus compras vigentes
 * (docs/compra/ticket-promedio.md). Sin compras vigentes no hay ticket: {@code valor} es nulo y
 * {@code sinDatos} es verdadero, en lugar de un cero o una división entre cero.
 */
public record TicketPromedio(BigDecimal valor, long compras, BigDecimal montoAcumulado, boolean sinDatos) {

    private static final int DECIMALES = 2;

    public static TicketPromedio de(ResumenCompras resumen) {
        if (resumen.compras() <= 0) {
            return new TicketPromedio(null, 0, BigDecimal.ZERO, true);
        }
        BigDecimal promedio = resumen.monto()
                .divide(BigDecimal.valueOf(resumen.compras()), DECIMALES, RoundingMode.HALF_UP);
        return new TicketPromedio(promedio, resumen.compras(), resumen.monto(), false);
    }
}
