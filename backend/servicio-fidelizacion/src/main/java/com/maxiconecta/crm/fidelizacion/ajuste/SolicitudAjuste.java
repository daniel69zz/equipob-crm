package com.maxiconecta.crm.fidelizacion.ajuste;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Ajuste manual del saldo que confirma el Administrador de CRM (SCRUM-429, docs/fidelizacion/ajuste-manual-puntos.md).
 * Las reglas que Bean Validation no cubre (puntos distintos de 0 y largo mínimo del motivo) las aplica
 * {@link ServicioAjustes}.
 */
public record SolicitudAjuste(
        @NotNull(message = "es obligatorio (lo genera la pantalla para cada ajuste)")
        UUID idSolicitud,

        @NotNull(message = "es obligatorio")
        @Min(value = -ServicioAjustes.PUNTOS_MAXIMOS, message = "admite hasta 100000 puntos por ajuste")
        @Max(value = ServicioAjustes.PUNTOS_MAXIMOS, message = "admite hasta 100000 puntos por ajuste")
        Long puntos,

        @NotBlank(message = "es obligatorio")
        @Size(max = 250, message = "admite hasta 250 caracteres")
        String motivo,

        @Size(max = 64, message = "admite hasta 64 caracteres")
        String referencia,

        @NotNull(message = "es obligatorio (saldo disponible que se revisó al confirmar)")
        @PositiveOrZero(message = "no puede ser negativo")
        Long saldoActual) {
}
