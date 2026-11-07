package com.maxiconecta.crm.fidelizacion.regla;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Nueva regla de acumulación enviada por el Administrador de CRM (SCRUM-392). Validación de los
 * parámetros (SCRUM-395, criterio de aceptación 2): los valores vacíos o negativos se rechazan indicando
 * el campo. {@code version} es la versión vigente que el administrador estaba editando: si otra persona
 * guardó antes una regla nueva, el cambio se rechaza en vez de pisarla.
 */
public record SolicitudReglaAcumulacion(
        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "0.01", message = "debe ser mayor que cero")
        @Digits(integer = 12, fraction = 2, message = "admite hasta 12 enteros y 2 decimales")
        BigDecimal bolivianosPorPunto,

        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "0.00", message = "no puede ser negativo")
        @Digits(integer = 12, fraction = 2, message = "admite hasta 12 enteros y 2 decimales")
        BigDecimal montoMinimo,

        @Size(max = 250, message = "admite hasta 250 caracteres")
        String motivo,

        @NotNull(message = "es obligatoria (versión vigente que se está editando)")
        @Positive(message = "debe ser mayor que cero")
        Long version) {
}
