package com.maxiconecta.crm.fidelizacion.comun;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ManejadorDeErrores {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> parametroInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest solicitud) {
        return respuesta(HttpStatus.BAD_REQUEST, "Valor no válido para '" + ex.getName() + "': " + ex.getValue(), solicitud);
    }

    /** Validación del cuerpo: el mensaje y {@code campos} indican cada campo rechazado. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> cuerpoInvalido(MethodArgumentNotValidException ex, HttpServletRequest solicitud) {
        Map<String, String> campos = new TreeMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            campos.merge(error.getField(), String.valueOf(error.getDefaultMessage()), (a, b) -> a + "; " + b);
        }
        String mensaje = campos.isEmpty() ? "La solicitud no es válida"
                : campos.entrySet().stream().map(c -> c.getKey() + ": " + c.getValue()).collect(Collectors.joining(". "));
        HttpStatus estado = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(estado).body(ApiError.de(estado.value(), estado.getReasonPhrase(), mensaje,
                solicitud.getRequestURI(), campos.isEmpty() ? null : campos));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> cuerpoIlegible(HttpMessageNotReadableException ex, HttpServletRequest solicitud) {
        return respuesta(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es un JSON válido o tiene valores de tipo "
                + "incorrecto", solicitud);
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ApiError> conflicto(ConflictoException ex, HttpServletRequest solicitud) {
        HttpStatus estado = HttpStatus.CONFLICT;
        return ResponseEntity.status(estado).body(ApiError.de(estado.value(), estado.getReasonPhrase(), ex.getMessage(),
                solicitud.getRequestURI(), ex.campos().isEmpty() ? null : ex.campos()));
    }

    @ExceptionHandler(ReglaInvalidaException.class)
    public ResponseEntity<ApiError> reglaInvalida(ReglaInvalidaException ex, HttpServletRequest solicitud) {
        HttpStatus estado = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(estado).body(ApiError.de(estado.value(), estado.getReasonPhrase(), ex.getMessage(),
                solicitud.getRequestURI(), ex.campos().isEmpty() ? null : ex.campos()));
    }

    /** Errores con estado explícito (401, 403, 404, 409…) con el mismo cuerpo que los demás. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> conEstado(ResponseStatusException ex, HttpServletRequest solicitud) {
        HttpStatusCode codigo = ex.getStatusCode();
        HttpStatus estado = HttpStatus.resolve(codigo.value());
        return ResponseEntity.status(codigo).body(ApiError.de(codigo.value(),
                estado != null ? estado.getReasonPhrase() : String.valueOf(codigo.value()), ex.getReason(),
                solicitud.getRequestURI()));
    }

    private static ResponseEntity<ApiError> respuesta(HttpStatus estado, String mensaje, HttpServletRequest solicitud) {
        return ResponseEntity.status(estado)
                .body(ApiError.de(estado.value(), estado.getReasonPhrase(), mensaje, solicitud.getRequestURI()));
    }
}
