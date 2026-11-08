package com.maxiconecta.crm.interacciones.comun;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Cuerpo de las respuestas de error, con el mismo formato que el API Gateway. {@code campos} indica,
 * cuando los hay, los campos rechazados y el motivo de cada uno.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(OffsetDateTime fecha, int estado, String error, String mensaje, String ruta,
                       Map<String, String> campos) {

    public static ApiError de(int estado, String error, String mensaje, String ruta) {
        return new ApiError(OffsetDateTime.now(), estado, error, mensaje, ruta, null);
    }

    public static ApiError de(int estado, String error, String mensaje, String ruta, Map<String, String> campos) {
        return new ApiError(OffsetDateTime.now(), estado, error, mensaje, ruta, campos);
    }
}
