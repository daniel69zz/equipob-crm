package com.maxiconecta.crm.comportamiento.compra;

import com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentificadorTest {

    @Test
    void tomaElIdentificadorDelClienteEnMarketplaceYVentas() {
        assertThat(Identificador.parsear("CLI-5521").idClienteOrigen()).isEqualTo("CLI-5521");
    }

    @Test
    void recortaLosEspacios() {
        assertThat(Identificador.parsear(" mp-user-3307 ").idClienteOrigen()).isEqualTo("mp-user-3307");
    }

    @Test
    void rechazaUnIdentificadorVacioODemasiadoLargo() {
        assertThatThrownBy(() -> Identificador.parsear("")).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> Identificador.parsear("   ")).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> Identificador.parsear(null)).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> Identificador.parsear("x".repeat(65))).isInstanceOf(ReglaNegocioException.class);
    }
}
