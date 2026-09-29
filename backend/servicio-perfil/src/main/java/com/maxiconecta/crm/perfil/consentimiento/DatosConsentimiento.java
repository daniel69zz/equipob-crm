package com.maxiconecta.crm.perfil.consentimiento;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Datos de un otorgamiento o actualización ya validados y con los valores por defecto aplicados
 * (ver {@link ValidadorConsentimiento}).
 *
 * @param vigenciaHasta último día de vigencia, o null si no vence
 */
public record DatosConsentimiento(CanalConsentimiento canal, Set<AlcanceConsentimiento> alcances,
                                  OffsetDateTime fechaOtorgamiento, LocalDate vigenciaDesde, LocalDate vigenciaHasta) {

    public DatosConsentimiento {
        alcances = Set.copyOf(alcances);
    }
}
