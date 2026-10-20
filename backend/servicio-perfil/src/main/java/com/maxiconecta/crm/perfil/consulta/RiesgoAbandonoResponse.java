package com.maxiconecta.crm.perfil.consulta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Riesgo de abandono del cliente (BAJO, MEDIO o ALTO) con los factores que lo explican, tomado de
 * servicio-comportamiento (SCRUM-35, docs/compra/riesgo-abandono.md). {@code enSeguimientoDesde} es la
 * fecha en que entró en riesgo ALTO. {@code sinDatos=true} cuando el cliente no tiene riesgo calculado,
 * no tiene identificadores de origen o el servicio no respondió (la ficha integral no debe fallar por
 * eso: CA2 de SCRUM-10).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RiesgoAbandonoResponse(String nivel, BigDecimal puntaje, boolean inactivo,
                                     OffsetDateTime enSeguimientoDesde, OffsetDateTime calculadoEn,
                                     List<Factor> factores, boolean sinDatos) {

    static final RiesgoAbandonoResponse SIN_DATOS = new RiesgoAbandonoResponse(null, null, false, null, null,
            List.of(), true);

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Factor(String factor, BigDecimal puntos, BigDecimal maximo, String descripcion) {
    }
}
