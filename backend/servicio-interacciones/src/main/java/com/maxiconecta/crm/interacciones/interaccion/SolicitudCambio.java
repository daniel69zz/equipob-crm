package com.maxiconecta.crm.interacciones.interaccion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PUT /api/interacciones/clientes/{clienteId}/interacciones/{id}}: los campos editables y la versión que el usuario tenía en pantalla.
 * El cliente no se cambia: una interacción registrada en otro cliente por error se corrige registrando otra.
 */
public record SolicitudCambio(
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = TipoInteraccion.PATRON, message = TipoInteraccion.MENSAJE)
        String tipo,
        @Pattern(regexp = Canal.PATRON, message = Canal.MENSAJE)
        String canal,
        @Size(max = 120, message = "admite hasta 120 caracteres")
        String asunto,
        @NotBlank(message = "es obligatoria: describa la consulta, el reclamo o la nota")
        String descripcion,
        @Size(max = 64, message = "admite hasta 64 caracteres")
        String referenciaCompra,
        @NotNull(message = "es obligatoria: la versión de la interacción que se está editando")
        @Positive(message = "debe ser mayor que 0")
        Long version) {
}
