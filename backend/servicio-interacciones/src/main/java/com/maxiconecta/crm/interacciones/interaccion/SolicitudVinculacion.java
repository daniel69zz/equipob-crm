package com.maxiconecta.crm.interacciones.interaccion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PUT /api/interacciones/clientes/{clienteId}/interacciones/{id}/compra} (SCRUM-446): la referencia
 * de la compra elegida del historial del cliente y la versión que el usuario tenía en pantalla.
 */
public record SolicitudVinculacion(
        @NotBlank(message = "es obligatoria: la referencia de la compra que se vincula")
        @Size(max = 64, message = "admite hasta 64 caracteres")
        String referencia,
        @NotNull(message = "es obligatoria: la versión de la interacción que se está actualizando")
        @Positive(message = "debe ser mayor que 0")
        Long version) {
}
