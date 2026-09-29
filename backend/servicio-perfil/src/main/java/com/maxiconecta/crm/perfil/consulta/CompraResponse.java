package com.maxiconecta.crm.perfil.consulta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Compra de un cliente, espejo de {@code HistorialComprasController.CompraResponse} de
 * servicio-comportamiento (docs/compra/historial-compras.md), usada para consolidar la ficha
 * integral (SCRUM-151).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CompraResponse(OffsetDateTime fecha, String referencia, BigDecimal montoTotal, String estado,
                             List<ItemResponse> items) {
}
