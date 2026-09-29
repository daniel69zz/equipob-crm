package com.maxiconecta.crm.comportamiento.compra;

import java.math.BigDecimal;

/**
 * Unidades y monto de una categoría dentro de una compra: lo comprado o lo devuelto, según de
 * qué consulta salga.
 */
public record LineaCategoria(long idCompra, String categoria, long unidades, BigDecimal monto) {
}
