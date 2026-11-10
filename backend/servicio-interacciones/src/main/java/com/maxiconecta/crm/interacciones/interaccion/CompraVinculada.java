package com.maxiconecta.crm.interacciones.interaccion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Compra del historial del cliente vinculada a una interacción (SCRUM-54, docs/interacciones/vinculacion-compras.md):
 * sus datos tal como estaban al vincularla, y quién la vinculó y cuándo (criterio de aceptación 3).
 */
public record CompraVinculada(String referencia, OffsetDateTime fecha, BigDecimal montoTotal, String estado,
                              String vinculadaPor, OffsetDateTime vinculadaEn) {
}
