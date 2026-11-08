package com.maxiconecta.crm.interacciones.interaccion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/interacciones/clientes/{clienteId}/interacciones} (SCRUM-432). El cliente va en la
 * ruta; el responsable y la fecha no se envían: salen de la sesión validada por el Gateway y del reloj de la base.
 */
public record SolicitudInteraccion(
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
        String referenciaCompra) {
}
