package com.maxiconecta.crm.fidelizacion.movimiento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SCRUM-592 · Regla de cálculo de puntos (SCRUM-386) con la tasa por defecto: 1 punto por cada Bs 10. */
class ReglaCalculoPuntosTest {

    private final ReglaCalculoPuntos regla = new ReglaCalculoPuntos(new BigDecimal("10"));

    @ParameterizedTest
    @CsvSource({"0.01, 0", "9.99, 0", "10.00, 1", "19.99, 1", "129.90, 12", "350.50, 35", "1000.00, 100",
            "999999999999.99, 99999999999"})
    void unPuntoPorCadaTramoCompletoRedondeandoHaciaAbajoYSinTope(String valor, long puntos) {
        assertThat(regla.puntos(new BigDecimal(valor))).isEqualTo(puntos);
    }

    @Test
    void laTasaEsConfigurable() {
        assertThat(new ReglaCalculoPuntos(new BigDecimal("2.50")).puntos(new BigDecimal("10.00"))).isEqualTo(4);
    }

    @Test
    void unValorNoPositivoOUnaTasaInvalidaSeRechazan() {
        assertThatThrownBy(() -> regla.puntos(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> regla.puntos(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReglaCalculoPuntos(BigDecimal.ZERO)).isInstanceOf(IllegalStateException.class);
    }
}
