package com.maxiconecta.crm.comportamiento.metrica;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Métricas vigentes de un cliente después de una compra o una anulación, que se publican para que
 * Segmentación reevalúe su segmento (SCRUM-26, docs/segmentacion/asignacion-automatica.md).
 * <p>
 * Es una foto completa, no un incremento: aplicarla dos veces da el mismo segmento.
 * {@code idEvento} es el del evento de venta o anulación que la provocó y sirve de clave de
 * idempotencia en Segmentación. {@code clasificacionFrecuencia} es la de SCRUM-38 ({@code OCASIONAL},
 * {@code HABITUAL} o {@code FRECUENTE}), o {@code null} si el cliente no tiene compras vigentes.
 */
public record MetricasCliente(String idEvento, String tipoEventoOrigen, long idCliente,
                              List<String> identificadoresOrigen, long frecuencia, BigDecimal valor,
                              List<String> categorias, OffsetDateTime calculadasEn,
                              String clasificacionFrecuencia) {
}
