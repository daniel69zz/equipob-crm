package com.maxiconecta.crm.fidelizacion.comun;

import java.time.OffsetDateTime;

/**
 * Cuerpo de las respuestas de error, con el mismo formato que el API Gateway.
 */
public record ApiError(OffsetDateTime fecha, int estado, String error, String mensaje, String ruta) {

    public static ApiError de(int estado, String error, String mensaje, String ruta) {
        return new ApiError(OffsetDateTime.now(), estado, error, mensaje, ruta);
    }
}
