package com.maxiconecta.crm.comportamiento.comparacion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-354, SCRUM-358 · Reglas de la variación absoluta y porcentual entre dos periodos
 * (SCRUM-40), incluidos los periodos en cero, sin base de datos.
 */
class VariacionConsumoTest {

    @Test
    void unaMetricaQueCreceTieneVariacionPositiva() {
        VariacionConsumo variacion = VariacionConsumo.calcular(new BigDecimal("480.50"), new BigDecimal("900.00"));

        assertThat(variacion.variacionAbsoluta()).isEqualByComparingTo("419.50");
        assertThat(variacion.variacionPorcentual()).isEqualByComparingTo("87.30");
        assertThat(variacion.tendencia()).isEqualTo(TendenciaConsumo.CRECE);
        assertThat(variacion.periodoA()).isEqualByComparingTo("480.50");
        assertThat(variacion.periodoB()).isEqualByComparingTo("900.00");
    }

    @Test
    void unaMetricaQueDecaeTieneVariacionNegativa() {
        VariacionConsumo variacion = VariacionConsumo.calcular(5, 3);

        assertThat(variacion.variacionAbsoluta()).isEqualByComparingTo("-2");
        assertThat(variacion.variacionPorcentual()).isEqualByComparingTo("-40.00");
        assertThat(variacion.tendencia()).isEqualTo(TendenciaConsumo.DECAE);
    }

    @Test
    void sinCambioLaVariacionEsCeroYLaTendenciaEstable() {
        VariacionConsumo variacion = VariacionConsumo.calcular(4, 4);

        assertThat(variacion.variacionAbsoluta()).isEqualByComparingTo("0");
        assertThat(variacion.variacionPorcentual()).isEqualByComparingTo("0.00");
        assertThat(variacion.tendencia()).isEqualTo(TendenciaConsumo.ESTABLE);
    }

    @Test
    void elPorcentajeSeRedondeaADosDecimales() {
        assertThat(VariacionConsumo.calcular(3, 4).variacionPorcentual()).isEqualByComparingTo("33.33");
        assertThat(VariacionConsumo.calcular(3, 5).variacionPorcentual()).isEqualByComparingTo("66.67");
        assertThat(VariacionConsumo.calcular(new BigDecimal("3"), new BigDecimal("2")).variacionPorcentual())
                .isEqualByComparingTo("-33.33");
    }

    @Test
    void perderTodoElPeriodoComparadoEsMenosCienPorCiento() {
        VariacionConsumo variacion = VariacionConsumo.calcular(new BigDecimal("250.00"), BigDecimal.ZERO);

        assertThat(variacion.variacionAbsoluta()).isEqualByComparingTo("-250.00");
        assertThat(variacion.variacionPorcentual()).isEqualByComparingTo("-100.00");
        assertThat(variacion.tendencia()).isEqualTo(TendenciaConsumo.DECAE);
    }

    @Test
    void unPeriodoBaseEnCeroNoDaErrorYSuTendenciaEsNuevo() {
        VariacionConsumo variacion = VariacionConsumo.calcular(BigDecimal.ZERO, new BigDecimal("300.00"));

        assertThat(variacion.variacionAbsoluta()).isEqualByComparingTo("300.00");
        assertThat(variacion.variacionPorcentual()).isNull();
        assertThat(variacion.tendencia()).isEqualTo(TendenciaConsumo.NUEVO);
    }

    @Test
    void dosPeriodosEnCeroEstanEstablesConVariacionCero() {
        VariacionConsumo variacion = VariacionConsumo.calcular(0, 0);

        assertThat(variacion.variacionAbsoluta()).isEqualByComparingTo("0");
        assertThat(variacion.variacionPorcentual()).isEqualByComparingTo("0.00");
        assertThat(variacion.tendencia()).isEqualTo(TendenciaConsumo.ESTABLE);
    }
}
