package com.maxiconecta.crm.interacciones.comun;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Valida un cuerpo con Bean Validation después de comprobar el acceso, para que un usuario sin permiso
 * reciba 403 aunque su solicitud además sea inválida. Los errores se informan por campo.
 */
@Component
public class Validaciones {

    private final Validator validator;

    public Validaciones(Validator validator) {
        this.validator = validator;
    }

    public void validar(Object cuerpo, String titulo) {
        if (cuerpo == null) {
            throw new ReglaInvalidaException(titulo + ": el cuerpo de la solicitud es obligatorio");
        }
        Map<String, String> campos = new TreeMap<>();
        for (ConstraintViolation<Object> violacion : validator.validate(cuerpo)) {
            campos.merge(violacion.getPropertyPath().toString(), violacion.getMessage(), (a, b) -> a + "; " + b);
        }
        if (!campos.isEmpty()) {
            throw new ReglaInvalidaException(titulo + ": " + campos.entrySet().stream()
                    .map(c -> c.getKey() + " " + c.getValue()).collect(Collectors.joining(", ")), campos);
        }
    }
}
