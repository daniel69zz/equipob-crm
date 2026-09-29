package com.maxiconecta.crm.comportamiento.ingesta;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Evento RIO-CRM-05 tal como llega de Marketplace y Ventas: anulación total o devolución parcial
 * de una compra. Los campos desconocidos se ignoran.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoAnulacionCompra(String idEvento, String tipoEvento, String origen,
                                    @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime fechaEmision,
                                    DatosAnulacion anulacion) {

    public static final String TIPO = "COMPRA_ANULADA";

    /**
     * @param tipo  {@code TOTAL} o {@code PARCIAL}; se lee como texto para informar un valor desconocido
     * @param items ítems devueltos; obligatorios solo en una devolución parcial
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DatosAnulacion(String idAnulacion, String idCompra, String idCliente, String tipo,
                                 @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime fecha,
                                 BigDecimal montoRevertido, String motivo,
                                 List<EventoCompraConfirmada.Item> items) {
    }
}
