package com.maxiconecta.crm.comportamiento.reporte;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;

public record SolicitudProgramacion(
        @NotBlank @Size(max = 120) String nombre,
        @NotNull Frecuencia frecuencia,
        @NotNull FormatoExportacion formato,
        @Min(1) @Max(1830) int dias,
        @Size(max = 60) String segmento,
        @NotNull OffsetDateTime inicio,
        OffsetDateTime fin) {
    public enum Frecuencia { DIARIA, SEMANAL, MENSUAL }
}
