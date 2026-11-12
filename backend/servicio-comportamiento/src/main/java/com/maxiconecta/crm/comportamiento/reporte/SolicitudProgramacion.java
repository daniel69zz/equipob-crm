package com.maxiconecta.crm.comportamiento.reporte;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * Configuración de un reporte programado (SCRUM-61): el reporte de comportamiento de compra de los últimos
 * {@code dias} días completos, de un segmento o de toda la cartera, cada día, semana o mes desde {@code inicio}.
 */
public record SolicitudProgramacion(
        @NotBlank @Size(max = 120) String nombre,
        @NotNull Frecuencia frecuencia,
        @NotNull FormatoExportacion formato,
        @Min(1) @Max(1830) int dias,
        @Size(max = 60) String segmento,
        @NotNull OffsetDateTime inicio,
        OffsetDateTime fin) {

    /** Un segmento en blanco es toda la cartera: se guarda nulo, no como un segmento sin nombre. */
    public SolicitudProgramacion {
        if (segmento != null) {
            segmento = segmento.strip();
            if (segmento.isEmpty()) {
                segmento = null;
            }
        }
    }

    public enum Frecuencia { DIARIA, SEMANAL, MENSUAL }
}
