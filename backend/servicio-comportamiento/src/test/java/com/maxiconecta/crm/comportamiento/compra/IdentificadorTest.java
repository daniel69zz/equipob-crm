package com.maxiconecta.crm.comportamiento.compra;

import com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentificadorTest {

    @Test
    void parseaOrigenYClienteSeparadosPorDosPuntos() {
        Identificador identificador = Identificador.parsear("VENTAS:CLI-5521");

        assertThat(identificador.origen()).isEqualTo(Origen.VENTAS);
        assertThat(identificador.idClienteOrigen()).isEqualTo("CLI-5521");
    }

    @Test
    void elOrigenNoDistingueMayusculasYSeRecortanLosEspacios() {
        Identificador identificador = Identificador.parsear(" marketplace : mp-user-3307 ");

        assertThat(identificador.origen()).isEqualTo(Origen.MARKETPLACE);
        assertThat(identificador.idClienteOrigen()).isEqualTo("mp-user-3307");
    }

    @Test
    void rechazaUnTextoSinDosPuntosOSinAlgunoDeLosDosLados() {
        assertThatThrownBy(() -> Identificador.parsear("CLI-5521")).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> Identificador.parsear(":CLI-5521")).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> Identificador.parsear("VENTAS:")).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> Identificador.parsear(null)).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void rechazaUnOrigenQueNoExiste() {
        assertThatThrownBy(() -> Identificador.parsear("PUNTOS:CLI-5521"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("PUNTOS");
    }
}
