package com.maxiconecta.crm.comportamiento.comparacion;

import com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCRUM-354, SCRUM-358 · Validación de los dos periodos a comparar (SCRUM-40), sin base de datos.
 */
class PeriodosACompararTest {

    private static LocalDate d(String fecha) {
        return LocalDate.parse(fecha);
    }

    @Test
    void dosPeriodosValidosYSeparadosSeAceptan() {
        PeriodosAComparar periodos = PeriodosAComparar.de(d("2026-08-01"), d("2026-08-31"), d("2026-09-01"),
                d("2026-09-30"));

        assertThat(periodos.a().desde()).isEqualTo(d("2026-08-01"));
        assertThat(periodos.b().hasta()).isEqualTo(d("2026-09-30"));
    }

    @Test
    void elPeriodoBasePuedeSerPosteriorAlComparado() {
        assertThat(PeriodosAComparar.de(d("2026-09-01"), d("2026-09-30"), d("2026-08-01"), d("2026-08-31")))
                .isNotNull();
    }

    @Test
    void dosPeriodosDeDiasDistintosPeroConsecutivosNoSeSuperponen() {
        assertThat(PeriodosAComparar.de(d("2026-08-31"), d("2026-08-31"), d("2026-09-01"), d("2026-09-01")))
                .isNotNull();
    }

    @Test
    void unPeriodoIncompletoSeRechazaIndicandoCualFalta() {
        assertThatThrownBy(() -> PeriodosAComparar.de(null, d("2026-08-31"), d("2026-09-01"), d("2026-09-30")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("periodo A").hasMessageContaining("desdeA");
        assertThatThrownBy(() -> PeriodosAComparar.de(d("2026-08-01"), d("2026-08-31"), d("2026-09-01"), null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("periodo B").hasMessageContaining("hastaB");
    }

    @Test
    void unPeriodoAlRevesSeRechazaIndicandoCual() {
        assertThatThrownBy(() -> PeriodosAComparar.de(d("2026-08-31"), d("2026-08-01"), d("2026-09-01"),
                d("2026-09-30")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageStartingWith("Periodo A:").hasMessageContaining("no puede ser posterior");
        assertThatThrownBy(() -> PeriodosAComparar.de(d("2026-08-01"), d("2026-08-31"), d("2026-09-30"),
                d("2026-09-01")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageStartingWith("Periodo B:");
    }

    @Test
    void dosPeriodosQueSeSuperponenSeRechazan() {
        assertThatThrownBy(() -> PeriodosAComparar.de(d("2026-08-01"), d("2026-09-15"), d("2026-09-01"),
                d("2026-09-30")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no pueden superponerse");
        assertThatThrownBy(() -> PeriodosAComparar.de(d("2026-09-01"), d("2026-09-30"), d("2026-09-30"),
                d("2026-10-31")))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> PeriodosAComparar.de(d("2026-09-01"), d("2026-09-30"), d("2026-09-01"),
                d("2026-09-30")))
                .isInstanceOf(ReglaNegocioException.class);
    }
}
