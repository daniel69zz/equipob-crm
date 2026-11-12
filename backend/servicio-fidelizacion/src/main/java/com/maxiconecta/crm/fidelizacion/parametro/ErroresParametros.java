package com.maxiconecta.crm.fidelizacion.parametro;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Errores de los parámetros generales con el formato del API Gateway y, cuando los hay, el motivo por parámetro en
 * {@code campos}: 400 para un valor fuera de rango y 409 si otro usuario lo cambió mientras tanto (SCRUM-65).
 */
@RestControllerAdvice(assignableTypes = ParametrosController.class)
public class ErroresParametros {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Error(OffsetDateTime fecha, int estado, String error, String mensaje, String ruta,
                        Map<String, String> campos) {
    }

    @ExceptionHandler(ParametroInvalidoException.class)
    public ResponseEntity<Error> invalido(ParametroInvalidoException ex, HttpServletRequest solicitud) {
        return respuesta(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.campos(), solicitud);
    }

    @ExceptionHandler(ParametroDesactualizadoException.class)
    public ResponseEntity<Error> desactualizado(ParametroDesactualizadoException ex, HttpServletRequest solicitud) {
        return respuesta(HttpStatus.CONFLICT, ex.getMessage(), ex.campos(), solicitud);
    }

    private static ResponseEntity<Error> respuesta(HttpStatus estado, String mensaje, Map<String, String> campos,
                                                   HttpServletRequest solicitud) {
        return ResponseEntity.status(estado).body(new Error(OffsetDateTime.now(), estado.value(),
                estado.getReasonPhrase(), mensaje, solicitud.getRequestURI(), campos));
    }
}
