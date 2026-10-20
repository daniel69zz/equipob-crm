package com.maxiconecta.crm.comportamiento.riesgo;

import com.maxiconecta.crm.comportamiento.consumo.NivelConsumo;
import com.maxiconecta.crm.comportamiento.frecuencia.ClasificacionFrecuencia;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-323 · Seguimiento comercial de los clientes en riesgo alto (SCRUM-326): se marca al entrar en
 * ALTO, se conserva mientras siga ahí y se borra al bajar de nivel.
 */
class RiesgoAbandonoClienteTest {

    private static final OffsetDateTime LUNES = OffsetDateTime.parse("2026-10-19T08:30:00Z");

    @Test
    void unClienteNuevoEnRiesgoAltoQuedaEnSeguimientoDesdeEseCalculo() {
        RiesgoAbandonoCliente riesgo = new RiesgoAbandonoCliente(42L, evaluacion(NivelRiesgo.ALTO, "80"), LUNES);

        assertThat(riesgo.getEnSeguimientoDesde()).isEqualTo(LUNES);
        assertThat(riesgo.getCalculadoEn()).isEqualTo(LUNES);
    }

    @Test
    void mientrasSigueEnRiesgoAltoConservaLaFechaDeEntrada() {
        RiesgoAbandonoCliente riesgo = new RiesgoAbandonoCliente(42L, evaluacion(NivelRiesgo.ALTO, "80"), LUNES);

        boolean entra = riesgo.aplicar(evaluacion(NivelRiesgo.ALTO, "90"), LUNES.plusDays(1));

        assertThat(entra).isFalse();
        assertThat(riesgo.getEnSeguimientoDesde()).isEqualTo(LUNES);
        assertThat(riesgo.getPuntaje()).isEqualByComparingTo("90");
        assertThat(riesgo.getCalculadoEn()).isEqualTo(LUNES.plusDays(1));
    }

    @Test
    void alBajarDeNivelDejaElSeguimientoYAlVolverEntraDeNuevo() {
        RiesgoAbandonoCliente riesgo = new RiesgoAbandonoCliente(42L, evaluacion(NivelRiesgo.ALTO, "80"), LUNES);

        assertThat(riesgo.aplicar(evaluacion(NivelRiesgo.MEDIO, "40"), LUNES.plusDays(1))).isFalse();
        assertThat(riesgo.getEnSeguimientoDesde()).isNull();

        assertThat(riesgo.aplicar(evaluacion(NivelRiesgo.ALTO, "70"), LUNES.plusDays(5))).isTrue();
        assertThat(riesgo.getEnSeguimientoDesde()).isEqualTo(LUNES.plusDays(5));
    }

    @Test
    void unClienteEnRiesgoBajoOMedioNoQuedaEnSeguimiento() {
        RiesgoAbandonoCliente riesgo = new RiesgoAbandonoCliente(42L, evaluacion(NivelRiesgo.BAJO, "10"), LUNES);

        assertThat(riesgo.getEnSeguimientoDesde()).isNull();
        assertThat(riesgo.aplicar(evaluacion(NivelRiesgo.MEDIO, "50"), LUNES.plusDays(1))).isFalse();
        assertThat(riesgo.getEnSeguimientoDesde()).isNull();
    }

    private static EvaluacionRiesgo evaluacion(NivelRiesgo nivel, String puntaje) {
        return new EvaluacionRiesgo(new BigDecimal(puntaje), nivel, 30, LUNES.minusDays(30), 5,
                new BigDecimal("1500.00"), ClasificacionFrecuencia.HABITUAL, NivelConsumo.MEDIO, false, false,
                new BigDecimal(puntaje), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
