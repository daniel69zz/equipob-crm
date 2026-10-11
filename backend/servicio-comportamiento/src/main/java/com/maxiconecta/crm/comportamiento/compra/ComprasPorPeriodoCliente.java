package com.maxiconecta.crm.comportamiento.compra;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Compras vigentes de un cliente (agrupado por {@code idClienteOrigen}) en la línea base y en el
 * periodo actual, tal como las agrega {@link AgregadoComprasCliente#comprasPorPeriodo}: la entrada
 * de la detección de cambios de comportamiento (docs/compra/cambios-comportamiento.md).
 * {@code primeraCompra} es la compra vigente más antigua del cliente, para saber cuánto de la línea
 * base llegó a observarse.
 */
public record ComprasPorPeriodoCliente(String idClienteOrigen, OffsetDateTime primeraCompra,
                                       Long comprasBase, BigDecimal montoBase,
                                       Long comprasActual, BigDecimal montoActual) {
}
