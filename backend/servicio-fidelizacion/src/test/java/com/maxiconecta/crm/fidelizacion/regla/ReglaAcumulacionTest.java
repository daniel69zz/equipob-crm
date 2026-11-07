package com.maxiconecta.crm.fidelizacion.regla;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SCRUM-394 · Cálculo de puntos por consumo con la regla configurada (tasa y monto mínimo de compra). */
class ReglaAcumulacionTest {

    static ReglaAcumulacion regla(String bolivianosPorPunto, String montoMinimo) {
        return new ReglaAcumulacion(1, new BigDecimal(bolivianosPorPunto), new BigDecimal(montoMinimo), null, "sistema",
                OffsetDateTime.parse("2026-11-01T00:00:00Z"));
    }

    @ParameterizedTest
    @CsvSource({"0.01, 0", "9.99, 0", "10.00, 1", "19.99, 1", "129.90, 12", "350.50, 35", "1000.00, 100",
            "999999999999.99, 99999999999"})
    void laReglaInicialDaUnPuntoPorCadaTramoCompletoSinTope(String valor, long puntos) {
        assertThat(regla("10", "0").puntos(new BigDecimal(valor))).isEqualTo(puntos);
    }

    @ParameterizedTest
    @CsvSource({"49.99, 0", "50.00, 10", "50.01, 10", "129.90, 25"})
    void unaCompraMenorQueElMontoMinimoNoGeneraPuntosYElMinimoEstaIncluido(String valor, long puntos) {
        assertThat(regla("5", "50").puntos(new BigDecimal(valor))).isEqualTo(puntos);
    }

    @Test
    void laTasaAdmiteDecimales() {
        assertThat(regla("2.50", "0").puntos(new BigDecimal("10.00"))).isEqualTo(4);
    }

    @Test
    void valoresYReglasInvalidosSeRechazan() {
        assertThatThrownBy(() -> regla("10", "0").puntos(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> regla("10", "0").puntos(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> regla("0", "0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> regla("10", "-1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comparaLosValoresSinImportarLaEscala() {
        assertThat(regla("10", "0").mismosValores(new BigDecimal("10.00"), new BigDecimal("0.00"))).isTrue();
        assertThat(regla("10", "0").mismosValores(new BigDecimal("10.00"), new BigDecimal("1"))).isFalse();
    }
}
