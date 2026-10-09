package com.maxiconecta.crm.comportamiento.compra;

import java.math.BigDecimal;

/**
 * Unidades y monto de una categoría dentro de una compra: lo comprado o lo devuelto, según de qué
 * consulta salga (docs/compra/evolucion-consumo-categoria.md).
 */
public record LineaConsumoCategoria(long idCompra, String categoria, long unidades, BigDecimal monto) {
}
