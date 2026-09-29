package com.maxiconecta.crm.comportamiento.indicador;

import java.math.BigDecimal;

/**
 * Consumo vigente de un cliente en una categoría: en cuántas compras aparece, sus unidades y su
 * monto (docs/compra/categorias-mas-consumidas.md).
 */
public record CategoriaConsumida(String categoria, long compras, long unidades, BigDecimal monto,
                                 boolean sinCategoria) {
}
