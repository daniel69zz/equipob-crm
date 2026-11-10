package com.maxiconecta.crm.interacciones.interaccion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PUT /api/interacciones/clientes/{clienteId}/interacciones/{id}/responsable} (SCRUM-457): el
 * usuario del agente responsable, un comentario opcional para él y la versión que el usuario tenía en pantalla.
 */
public record SolicitudAsignacion(
        @NotBlank(message = "es obligatorio: el usuario del agente responsable")
        @Size(max = 100, message = "admite hasta 100 caracteres")
        String responsable,
        @Size(max = 500, message = "admite hasta 500 caracteres")
        String comentario,
        @NotNull(message = "es obligatoria: la versión de la interacción que se está actualizando")
        @Positive(message = "debe ser mayor que 0")
        Long version) {
}
