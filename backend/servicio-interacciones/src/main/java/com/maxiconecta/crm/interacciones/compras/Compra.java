package com.maxiconecta.crm.interacciones.compras;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Compra del cliente, espejo de {@code HistorialComprasController.CompraResponse} de servicio-comportamiento
 * (docs/compra/historial-compras.md).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Compra(OffsetDateTime fecha, String referencia, BigDecimal montoTotal, String estado,
                     List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String categoria, int cantidad, BigDecimal monto) {
    }
}
