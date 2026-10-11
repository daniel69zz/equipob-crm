package com.maxiconecta.crm.comportamiento.cohorte;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-330, SCRUM-332 · Cálculo de la retención por cohortes, sin base de datos. Reproduce el
 * cálculo manual de referencia de docs/compra/retencion-cohortes.md (mes en curso: 2026-10).
 */
class CalculadoraRetencionTest {

    private static final YearMonth JUL = YearMonth.of(2026, 7);
    private static final YearMonth AGO = YearMonth.of(2026, 8);
    private static final YearMonth SEP = YearMonth.of(2026, 9);
    private static final YearMonth OCT = YearMonth.of(2026, 10);

    /** Actividad que agrega la base para los clientes A a I del cálculo manual. */
    private static final List<ActividadCohorte> CALCULO_MANUAL = List.of(
            new ActividadCohorte(JUL, JUL, 4), new ActividadCohorte(JUL, AGO, 2),
            new ActividadCohorte(JUL, SEP, 1), new ActividadCohorte(JUL, OCT, 1),
            new ActividadCohorte(AGO, AGO, 3), new ActividadCohorte(AGO, SEP, 1),
            new ActividadCohorte(SEP, SEP, 1), new ActividadCohorte(SEP, OCT, 1),
            new ActividadCohorte(OCT, OCT, 1));

    @Test
    void reproduceLaMatrizDelCalculoManual() {
        List<CalculadoraRetencion.CohorteRetencion> matriz = CalculadoraRetencion.calcular(CALCULO_MANUAL, JUL, OCT, OCT);

        assertThat(matriz).extracting(CalculadoraRetencion.CohorteRetencion::cohorte).containsExactly(JUL, AGO, SEP, OCT);
        assertThat(matriz).extracting(CalculadoraRetencion.CohorteRetencion::clientes).containsExactly(4L, 3L, 1L, 1L);
        assertFila(matriz.get(0), "100.00", "50.00", "25.00", "25.00");
        assertFila(matriz.get(1), "100.00", "33.33", "0.00");
        assertFila(matriz.get(2), "100.00", "100.00");
        assertFila(matriz.get(3), "100.00");
        assertThat(matriz.get(0).periodos()).extracting(CalculadoraRetencion.RetencionPeriodo::clientes)
                .containsExactly(4L, 2L, 1L, 1L);
        assertThat(matriz.get(1).periodos().get(2).mes()).isEqualTo(OCT);
    }

    @Test
    void unMesSinComprasEsCeroPorCientoYNoSeSaltea() {
        List<CalculadoraRetencion.CohorteRetencion> matriz = CalculadoraRetencion.calcular(
                List.of(new ActividadCohorte(JUL, JUL, 2), new ActividadCohorte(JUL, SEP, 1)), JUL, JUL, OCT);

        assertThat(matriz.get(0).periodos()).extracting(CalculadoraRetencion.RetencionPeriodo::periodo)
                .containsExactly(0, 1, 2, 3);
        assertFila(matriz.get(0), "100.00", "0.00", "50.00", "0.00");
    }

    @Test
    void lasCohortesSinClientesNuevosAparecenVaciasParaNoDejarHuecos() {
        List<CalculadoraRetencion.CohorteRetencion> matriz = CalculadoraRetencion.calcular(
                List.of(new ActividadCohorte(JUL, JUL, 1)), JUL, SEP, OCT);

        assertThat(matriz).hasSize(3);
        assertThat(matriz.get(1).cohorte()).isEqualTo(AGO);
        assertThat(matriz.get(1).clientes()).isZero();
        assertThat(matriz.get(1).periodos()).isEmpty();
    }

    @Test
    void soloSeCalculanLosPeriodosHastaElMesEnCurso() {
        List<CalculadoraRetencion.CohorteRetencion> matriz = CalculadoraRetencion.calcular(CALCULO_MANUAL, JUL, JUL, AGO);

        assertThat(matriz.get(0).periodos()).hasSize(2);
    }

    @Test
    void elPorcentajeSeRedondeaADosDecimales() {
        assertThat(CalculadoraRetencion.porcentaje(1, 3)).isEqualByComparingTo("33.33");
        assertThat(CalculadoraRetencion.porcentaje(2, 3)).isEqualByComparingTo("66.67");
        assertThat(CalculadoraRetencion.porcentaje(0, 7)).isEqualByComparingTo("0.00");
    }

    private static void assertFila(CalculadoraRetencion.CohorteRetencion fila, String... porcentajes) {
        assertThat(fila.periodos()).extracting(p -> p.porcentaje().toPlainString()).containsExactly(porcentajes);
    }
}
