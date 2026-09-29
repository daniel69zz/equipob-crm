package com.maxiconecta.crm.comportamiento.compra;

import java.time.OffsetDateTime;

/**
 * Última compra vigente de un cliente en un canal (agrupada por {@code origen} e
 * {@code idClienteOrigen}), tal como la agrega {@link AgregadoComprasCliente}. La usan los
 * indicadores que necesitan recorrer a todos los clientes, no solo a uno (por ejemplo, la
 * detección de clientes inactivos, ver docs/compra/clientes-inactivos.md).
 */
public record UltimaCompraCliente(Origen origen, String idClienteOrigen, OffsetDateTime ultimaCompra) {
}
