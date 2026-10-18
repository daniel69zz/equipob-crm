package com.maxiconecta.crm.comportamiento.consumo;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class UmbralesConsumoControllerTest {
    @Test void devuelveLaMismaConfiguracionQueUsaLaFicha() {
        var criterio = new CriterioNivelConsumo(new BigDecimal("750"), new BigDecimal("9000"));
        var resultado = new UmbralesConsumoController(criterio).obtener();
        assertThat(resultado.medio()).isEqualByComparingTo("750");
        assertThat(resultado.alto()).isEqualByComparingTo("9000");
        assertThat(criterio.clasificar(resultado.medio())).contains(NivelConsumo.MEDIO);
        assertThat(criterio.clasificar(resultado.alto())).contains(NivelConsumo.ALTO);
    }
}
