package com.maxiconecta.crm.fidelizacion.nivel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Nueva configuración de niveles enviada por el Administrador de CRM (SCRUM-413). Los rangos se validan
 * después con {@link ReglasNiveles}. {@code version} es la versión vigente que se estaba editando.
 */
public record SolicitudNiveles(
        @NotNull(message = "es obligatorio")
        @Min(value = 1, message = "debe ser de al menos 1 mes")
        @Max(value = 120, message = "admite hasta 120 meses")
        Integer periodoMeses,

        @NotEmpty(message = "debe incluir los umbrales de Bronce, Plata y Oro")
        List<@Valid @NotNull(message = "no puede estar vacío") UmbralNivel> niveles,

        @Size(max = 250, message = "admite hasta 250 caracteres")
        String motivo,

        @NotNull(message = "es obligatoria (versión vigente que se está editando)")
        @Positive(message = "debe ser mayor que cero")
        Long version) {
}
