package com.maxiconecta.crm.fidelizacion.movimiento;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Evento RIO-CRM-03 tal como llega de Marketplace y Ventas
 * (docs/contratos-eventos/RIO-CRM-03-movimiento-puntos.md). Los campos desconocidos se ignoran.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoMovimientoPuntos(String idEvento, String tipoEvento,
                                     @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime fechaEmision,
                                     Movimiento movimiento) {

    public static final String TIPO = "MOVIMIENTO_PUNTOS";

    /**
     * {@code puntos} son los puntos redimidos de una redención (SCRUM-405). Se lee como decimal para
     * rechazar valores con fracción en lugar de truncarlos. {@code idTransaccionOriginal} es la compra que
     * revierte una reversa (SCRUM-524).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Movimiento(String idTransaccion, String idCliente, String tipoMovimiento,
                             BigDecimal valorConsumido, BigDecimal puntos, String idTransaccionOriginal,
                             @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime fecha) {
    }
}
