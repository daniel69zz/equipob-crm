package com.maxiconecta.crm.perfil.consentimiento;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Otorgamiento o actualización tal como llega al API. Los campos opcionales se completan en
 * {@link ValidadorConsentimiento}.
 *
 * @param vigenciaDesde     si falta: la del consentimiento vigente al actualizarlo, o hoy
 * @param vigenciaHasta     si falta, el consentimiento no vence
 * @param fechaOtorgamiento si falta: la del consentimiento vigente al actualizarlo, o el momento del registro
 */
public record SolicitudConsentimiento(@NotNull CanalConsentimiento canal, Set<AlcanceConsentimiento> alcances,
                                      LocalDate vigenciaDesde, LocalDate vigenciaHasta,
                                      OffsetDateTime fechaOtorgamiento) {
}
