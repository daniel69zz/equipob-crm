package com.maxiconecta.crm.comportamiento.compra;

import java.math.BigDecimal;

/**
 * Valor vigente que suman las compras de un cliente (agrupado por {@code idClienteOrigen}), tal
 * como lo agrega {@link AgregadoComprasCliente}. Lo usan los procesos que recorren a todos los
 * clientes en vez de a uno solo, como la clasificación por nivel de consumo
 * (docs/compra/nivel-consumo.md).
 */
public record ValorCliente(String idClienteOrigen, BigDecimal valor) {
}
