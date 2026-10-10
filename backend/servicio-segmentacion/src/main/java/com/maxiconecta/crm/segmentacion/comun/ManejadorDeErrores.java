package com.maxiconecta.crm.segmentacion.comun;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ManejadorDeErrores {

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ApiError> regla(org.springframework.web.server.ResponseStatusException ex, HttpServletRequest solicitud) {
        return respuesta(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason(), solicitud);
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validacion(org.springframework.web.bind.MethodArgumentNotValidException ex, HttpServletRequest solicitud) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage()).collect(java.util.stream.Collectors.joining("; "));
        return respuesta(HttpStatus.BAD_REQUEST, mensaje, solicitud);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> cuerpoInvalido(HttpServletRequest solicitud) {
        return respuesta(HttpStatus.BAD_REQUEST, "Revise los campos, indicadores y condiciones de la solicitud", solicitud);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> parametroInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest solicitud) {
        return respuesta(HttpStatus.BAD_REQUEST, "Valor no válido para '" + ex.getName() + "': " + ex.getValue(), solicitud);
    }

    private static ResponseEntity<ApiError> respuesta(HttpStatus estado, String mensaje, HttpServletRequest solicitud) {
        return ResponseEntity.status(estado)
                .body(ApiError.de(estado.value(), estado.getReasonPhrase(), mensaje, solicitud.getRequestURI()));
    }
}
