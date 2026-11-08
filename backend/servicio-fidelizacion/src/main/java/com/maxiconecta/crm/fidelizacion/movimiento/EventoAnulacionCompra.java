package com.maxiconecta.crm.fidelizacion.movimiento;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Evento RIO-CRM-05 de anulación total o devolución parcial de una compra
 * (docs/contratos-eventos/RIO-CRM-05-anulacion-compra.md). Fidelización solo usa lo necesario para la
 * reversa de puntos (SCRUM-524): los ítems devueltos no cambian los puntos, que salen del monto revertido.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoAnulacionCompra(String idEvento, String tipoEvento,
                                    @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime fechaEmision,
                                    Anulacion anulacion) {

    public static final String TIPO = "COMPRA_ANULADA";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Anulacion(String idAnulacion, String idCompra, String idCliente, String tipo,
                            @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime fecha,
                            BigDecimal montoRevertido) {
    }
}
