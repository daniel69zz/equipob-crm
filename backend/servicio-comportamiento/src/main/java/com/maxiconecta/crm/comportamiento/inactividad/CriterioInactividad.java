package com.maxiconecta.crm.comportamiento.inactividad;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Criterio de cliente inactivo (SCRUM-295, docs/compra/clientes-inactivos.md): no registra
 * ninguna compra vigente (estado distinto de ANULADA) en los últimos {@code umbralDias} días.
 */
@Component
public class CriterioInactividad {

    private final int umbralDias;
    private final Clock reloj;

    public CriterioInactividad(@Value("${comportamiento.inactividad.umbral-dias:90}") int umbralDias, Clock reloj) {
        this.umbralDias = umbralDias;
        this.reloj = reloj;
    }

    public int umbralDias() {
        return umbralDias;
    }

    /** Una última compra anterior a esta fecha hace al cliente inactivo. */
    public OffsetDateTime fechaCorte() {
        return OffsetDateTime.now(reloj).minusDays(umbralDias);
    }
}
