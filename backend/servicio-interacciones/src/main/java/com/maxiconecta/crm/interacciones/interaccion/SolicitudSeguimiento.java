package com.maxiconecta.crm.interacciones.interaccion;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PATCH /api/interacciones/clientes/{clienteId}/interacciones/{id}} (SCRUM-452): el estado de
 * seguimiento nuevo, el tipo nuevo, o ambos, con la versión que el usuario tenía en pantalla. Lo que no se envía no
 * cambia.
 */
public record SolicitudSeguimiento(
        @Pattern(regexp = EstadoInteraccion.PATRON, message = EstadoInteraccion.MENSAJE)
        String estado,
        @Pattern(regexp = TipoInteraccion.PATRON, message = TipoInteraccion.MENSAJE)
        String tipo,
        @Size(max = 500, message = "admite hasta 500 caracteres")
        String comentario,
        @NotNull(message = "es obligatoria: la versión de la interacción que se está actualizando")
        @Positive(message = "debe ser mayor que 0")
        Long version) {
}
