package com.maxiconecta.crm.perfil.comun;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ManejadorDeErrores {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ApiError> noEncontrado(RecursoNoEncontradoException ex, HttpServletRequest solicitud) {
        return respuesta(HttpStatus.NOT_FOUND, ex.getMessage(), solicitud);
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ApiError> conflicto(ConflictoException ex, HttpServletRequest solicitud) {
        return respuesta(HttpStatus.CONFLICT, ex.getMessage(), solicitud);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validacion(MethodArgumentNotValidException ex, HttpServletRequest solicitud) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return respuesta(HttpStatus.BAD_REQUEST, mensaje, solicitud);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> cuerpoIlegible(HttpMessageNotReadableException ex, HttpServletRequest solicitud) {
        return respuesta(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es válido", solicitud);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ApiError> reglaNegocio(ReglaNegocioException ex, HttpServletRequest solicitud) {
        return respuesta(HttpStatus.BAD_REQUEST, ex.getMessage(), solicitud);
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
