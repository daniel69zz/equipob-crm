package com.maxiconecta.crm.comportamiento.inactividad;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-295 · Criterio de cliente inactivo: umbral configurable de días sin compras vigentes.
 */
class CriterioInactividadTest {

    @Test
    void laFechaDeCorteEsHoyMenosElUmbralConfigurado() {
        Clock reloj = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC);
        CriterioInactividad criterio = new CriterioInactividad(90, reloj);

        assertThat(criterio.umbralDias()).isEqualTo(90);
        assertThat(criterio.fechaCorte()).isEqualTo("2026-07-01T12:00:00Z");
    }

    @Test
    void unUmbralDistintoCambiaLaFechaDeCorte() {
        Clock reloj = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC);
        CriterioInactividad criterio = new CriterioInactividad(30, reloj);

        assertThat(criterio.fechaCorte()).isEqualTo("2026-08-30T12:00:00Z");
    }
}
