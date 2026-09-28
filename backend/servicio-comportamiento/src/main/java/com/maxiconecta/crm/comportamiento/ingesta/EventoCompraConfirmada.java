package com.maxiconecta.crm.comportamiento.ingesta;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Evento RIO-CRM-02 tal como llega de Marketplace y Ventas.
 */
public record EventoCompraConfirmada(String idEvento, String tipoEvento, String origen, OffsetDateTime fechaEmision,
                                     DatosCompra compra) {

    public static final String TIPO = "COMPRA_CONFIRMADA";

    public record DatosCompra(String idCompra, String idCliente, OffsetDateTime fecha, BigDecimal montoTotal,
                              List<Item> items) {
    }

    public record Item(String categoria, Integer cantidad, BigDecimal monto) {
    }
}
