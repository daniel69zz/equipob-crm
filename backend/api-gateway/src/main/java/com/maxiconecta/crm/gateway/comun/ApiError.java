package com.maxiconecta.crm.gateway.comun;

import java.time.Instant;

/**
 * Formato común de las respuestas de error del API Gateway.
 */
public record ApiError(Instant fecha, int estado, String error, String mensaje, String ruta) {

    public static ApiError de(int estado, String error, String mensaje, String ruta) {
        return new ApiError(Instant.now(), estado, error, mensaje, ruta);
    }
}
