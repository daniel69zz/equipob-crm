package com.maxiconecta.crm.comportamiento.ingesta;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Evento RIO-CRM-02 tal como llega de Marketplace y Ventas. Los campos desconocidos se ignoran,
 * para que el emisor pueda agregar campos sin romper al CRM.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoCompraConfirmada(String idEvento, String tipoEvento,
                                     @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime fechaEmision,
                                     DatosCompra compra) {

    public static final String TIPO = "COMPRA_CONFIRMADA";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DatosCompra(String idCompra, String idCliente,
                              @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime fecha,
                              BigDecimal montoTotal,
                              List<Item> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String categoria, Integer cantidad, BigDecimal monto) {
    }
}
