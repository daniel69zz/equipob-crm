package com.maxiconecta.crm.comportamiento.consumo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCRUM-272 · Clasificación por umbrales y casos límite del nivel de consumo (SCRUM-27), sin base
 * de datos. Umbrales por defecto: 500 (medio) y 5000 (alto).
 */
class CriterioNivelConsumoTest {

    private final CriterioNivelConsumo criterio = new CriterioNivelConsumo(new BigDecimal("500"),
            new BigDecimal("5000"));

    @Test
    void unValorPorDebajoDelUmbralMedioEsBajo() {
        assertThat(criterio.clasificar(new BigDecimal("0.01"))).contains(NivelConsumo.BAJO);
        assertThat(criterio.clasificar(new BigDecimal("499.99"))).contains(NivelConsumo.BAJO);
    }

    @Test
    void unValorExactamenteEnElUmbralMedioEsMedio() {
        assertThat(criterio.clasificar(new BigDecimal("500"))).contains(NivelConsumo.MEDIO);
        assertThat(criterio.clasificar(new BigDecimal("500.00"))).contains(NivelConsumo.MEDIO);
    }

    @Test
    void unValorEntreLosDosUmbralesEsMedio() {
        assertThat(criterio.clasificar(new BigDecimal("2500.50"))).contains(NivelConsumo.MEDIO);
        assertThat(criterio.clasificar(new BigDecimal("4999.99"))).contains(NivelConsumo.MEDIO);
    }

    @Test
    void unValorExactamenteEnElUmbralAltoEsAlto() {
        assertThat(criterio.clasificar(new BigDecimal("5000"))).contains(NivelConsumo.ALTO);
        assertThat(criterio.clasificar(new BigDecimal("5000.00"))).contains(NivelConsumo.ALTO);
    }

    @Test
    void unValorPorEncimaDelUmbralAltoEsAlto() {
        assertThat(criterio.clasificar(new BigDecimal("5000.01"))).contains(NivelConsumo.ALTO);
        assertThat(criterio.clasificar(new BigDecimal("1000000"))).contains(NivelConsumo.ALTO);
    }

    @Test
    void sinNadaVigenteNoHayNivelYNoEsUnError() {
        assertThat(criterio.clasificar(BigDecimal.ZERO)).isEqualTo(Optional.empty());
        assertThat(criterio.clasificar(null)).isEqualTo(Optional.empty());
        assertThat(criterio.clasificar(new BigDecimal("-1"))).isEqualTo(Optional.empty());
    }

    @Test
    void losUmbralesSePuedenConfigurar() {
        CriterioNivelConsumo propio = new CriterioNivelConsumo(new BigDecimal("100"), new BigDecimal("1000"));

        assertThat(propio.clasificar(new BigDecimal("99.99"))).contains(NivelConsumo.BAJO);
        assertThat(propio.clasificar(new BigDecimal("100"))).contains(NivelConsumo.MEDIO);
        assertThat(propio.clasificar(new BigDecimal("1000"))).contains(NivelConsumo.ALTO);
    }

    @Test
    void unosUmbralesIncoherentesImpidenArrancar() {
        assertThatThrownBy(() -> new CriterioNivelConsumo(BigDecimal.ZERO, new BigDecimal("5000")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CriterioNivelConsumo(new BigDecimal("5000"), new BigDecimal("500")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CriterioNivelConsumo(new BigDecimal("500"), new BigDecimal("500")))
                .isInstanceOf(IllegalStateException.class);
    }
}
