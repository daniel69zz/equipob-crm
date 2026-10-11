package com.maxiconecta.crm.comportamiento.periodo;

import com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCRUM-346, SCRUM-350 · Validación del rango de fechas de la consulta por periodo (criterio de
 * aceptación 4 de SCRUM-39), sin base de datos.
 */
class RangoPeriodoTest {

    private static final ZoneId LA_PAZ = ZoneId.of("America/La_Paz");

    @Test
    void unRangoValidoConservaSusFechas() {
        RangoPeriodo rango = new RangoPeriodo(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"));

        assertThat(rango.desde()).isEqualTo(LocalDate.parse("2026-09-01"));
        assertThat(rango.hasta()).isEqualTo(LocalDate.parse("2026-09-30"));
    }

    @Test
    void unPeriodoDeUnSoloDiaEsValido() {
        LocalDate dia = LocalDate.parse("2026-09-15");

        assertThat(new RangoPeriodo(dia, dia)).isNotNull();
    }

    @Test
    void faltarLaFechaDesdeSeRechaza() {
        assertThatThrownBy(() -> new RangoPeriodo(null, LocalDate.parse("2026-09-30")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("'desde'");
    }

    @Test
    void faltarLaFechaHastaSeRechaza() {
        assertThatThrownBy(() -> new RangoPeriodo(LocalDate.parse("2026-09-01"), null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("'hasta'");
    }

    @Test
    void unRangoAlReves() {
        assertThatThrownBy(() -> new RangoPeriodo(LocalDate.parse("2026-09-30"), LocalDate.parse("2026-09-01")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no puede ser posterior");
    }

    @Test
    void unRangoMasLargoQueElMaximoSeRechaza() {
        LocalDate desde = LocalDate.parse("2020-01-01");

        assertThatThrownBy(() -> new RangoPeriodo(desde, desde.plusDays(RangoPeriodo.DIAS_MAXIMOS)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("el máximo es");
    }

    @Test
    void unRangoDeExactamenteElMaximoSeAcepta() {
        LocalDate desde = LocalDate.parse("2020-01-01");

        assertThat(new RangoPeriodo(desde, desde.plusDays(RangoPeriodo.DIAS_MAXIMOS - 1))).isNotNull();
    }

    @Test
    void elPeriodoVaDeLasCeroHorasDeDesdeAlDiaSiguienteDeHastaEnLaZonaDelNegocio() {
        RangoPeriodo rango = new RangoPeriodo(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"));

        assertThat(rango.inicio(LA_PAZ)).isEqualTo(OffsetDateTime.parse("2026-09-01T00:00:00-04:00"));
        assertThat(rango.fin(LA_PAZ)).isEqualTo(OffsetDateTime.parse("2026-10-01T00:00:00-04:00"));
        assertThat(rango.inicio(LA_PAZ).toInstant()).isEqualTo(OffsetDateTime.parse("2026-09-01T04:00:00Z").toInstant());
    }
}
