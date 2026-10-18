package com.maxiconecta.crm.segmentacion.simulacion;

import com.maxiconecta.crm.segmentacion.criterio.SolicitudCriterio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** criterioId ausente: alta; presente: sustitución hipotética conservando su estado activo. */
public record SolicitudSimulacion(@Positive Long criterioId, @NotNull @Valid SolicitudCriterio regla) { }
