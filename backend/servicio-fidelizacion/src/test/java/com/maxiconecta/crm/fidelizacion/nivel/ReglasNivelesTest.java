package com.maxiconecta.crm.fidelizacion.nivel;

import com.maxiconecta.crm.fidelizacion.comun.ReglaInvalidaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * SCRUM-412 · Clasificación de clientes por nivel con los umbrales iniciales (Bronce 0–499, Plata
 * 500–1.999, Oro desde 2.000) y rechazo de umbrales que se superponen (criterio de aceptación 2).
 */
class ReglasNivelesTest {

    static List<UmbralNivel> iniciales() {
        return List.of(new UmbralNivel(Nivel.BRONCE, 0L, 499L), new UmbralNivel(Nivel.PLATA, 500L, 1999L),
                new UmbralNivel(Nivel.ORO, 2000L, null));
    }

    // --- Criterio 1: cada cliente queda en el nivel que le corresponde ---

    @ParameterizedTest
    @CsvSource({"0, BRONCE", "1, BRONCE", "499, BRONCE", "500, PLATA", "1999, PLATA", "2000, ORO", "1000000, ORO"})
    void cadaCantidadDePuntosQuedaEnSuNivelConLosLimitesIncluidos(long puntos, Nivel nivel) {
        assertThat(configuracion(iniciales()).nivelPara(puntos)).contains(nivel);
    }

    @Test
    void siElBronceEmpiezaPorEncimaDeCeroLosClientesConMenosPuntosQuedanSinNivel() {
        ConfiguracionNiveles configuracion = configuracion(List.of(new UmbralNivel(Nivel.BRONCE, 100L, 499L),
                new UmbralNivel(Nivel.PLATA, 500L, 1999L), new UmbralNivel(Nivel.ORO, 2000L, null)));

        assertThat(configuracion.nivelPara(99)).isEmpty();
        assertThat(configuracion.nivelPara(100)).contains(Nivel.BRONCE);
    }

    @Test
    void losUmbralesValidosSeDevuelvenOrdenadosDelNivelMasBajoAlMasAlto() {
        List<UmbralNivel> desordenados = List.of(iniciales().get(2), iniciales().get(0), iniciales().get(1));

        assertThat(ReglasNiveles.validar(desordenados)).extracting(UmbralNivel::nivel)
                .containsExactly(Nivel.BRONCE, Nivel.PLATA, Nivel.ORO);
    }

    // --- Criterio 2: umbrales que se superponen se rechazan indicando el conflicto ---

    @Test
    void unosUmbralesQueSeSuperponenSeRechazanIndicandoElConflicto() {
        ReglaInvalidaException error = catchThrowableOfType(() -> ReglasNiveles.validar(List.of(
                new UmbralNivel(Nivel.BRONCE, 0L, 500L), new UmbralNivel(Nivel.PLATA, 500L, 1999L),
                new UmbralNivel(Nivel.ORO, 2000L, null))), ReglaInvalidaException.class);

        assertThat(error.getMessage()).contains("Conflicto")
                .contains("Bronce (0–500) y Plata (500–1999) se superponen")
                .contains("el mínimo de Plata debe ser 501");
        assertThat(error.campos()).containsKey("PLATA.puntosMinimos");
    }

    @Test
    void unNivelSuperiorDentroDelRangoDelAnteriorTambienSeSuperpone() {
        assertThatThrownBy(() -> ReglasNiveles.validar(List.of(new UmbralNivel(Nivel.BRONCE, 0L, 499L),
                new UmbralNivel(Nivel.PLATA, 500L, 3000L), new UmbralNivel(Nivel.ORO, 2000L, null))))
                .isInstanceOf(ReglaInvalidaException.class)
                .hasMessageContaining("Plata (500–3000) y Oro (2000 o más) se superponen");
    }

    @Test
    void unHuecoEntreNivelesSeRechazaPorqueDejariaClientesSinNivel() {
        assertThatThrownBy(() -> ReglasNiveles.validar(List.of(new UmbralNivel(Nivel.BRONCE, 0L, 499L),
                new UmbralNivel(Nivel.PLATA, 600L, 1999L), new UmbralNivel(Nivel.ORO, 2000L, null))))
                .isInstanceOf(ReglaInvalidaException.class)
                .hasMessageContaining("quedarían sin nivel los clientes con 500–599 puntos");
    }

    @Test
    void faltanONivelesRepetidosSeRechazan() {
        assertThatThrownBy(() -> ReglasNiveles.validar(List.of(new UmbralNivel(Nivel.BRONCE, 0L, 499L),
                new UmbralNivel(Nivel.ORO, 500L, null))))
                .isInstanceOf(ReglaInvalidaException.class).hasMessageContaining("falta el umbral del nivel Plata");
        assertThatThrownBy(() -> ReglasNiveles.validar(List.of(new UmbralNivel(Nivel.BRONCE, 0L, 499L),
                new UmbralNivel(Nivel.BRONCE, 500L, 999L), new UmbralNivel(Nivel.PLATA, 1000L, 1999L),
                new UmbralNivel(Nivel.ORO, 2000L, null))))
                .hasMessageContaining("está repetido");
        assertThatThrownBy(() -> ReglasNiveles.validar(List.of())).isInstanceOf(ReglaInvalidaException.class);
    }

    @Test
    void soloElOroQuedaSinMaximoYElMaximoNoPuedeSerMenorQueElMinimo() {
        assertThatThrownBy(() -> ReglasNiveles.validar(List.of(new UmbralNivel(Nivel.BRONCE, 0L, null),
                new UmbralNivel(Nivel.PLATA, 500L, 1999L), new UmbralNivel(Nivel.ORO, 2000L, null))))
                .hasMessageContaining("BRONCE.puntosMaximos es obligatorio");
        assertThatThrownBy(() -> ReglasNiveles.validar(List.of(new UmbralNivel(Nivel.BRONCE, 0L, 499L),
                new UmbralNivel(Nivel.PLATA, 500L, 1999L), new UmbralNivel(Nivel.ORO, 2000L, 9999L))))
                .hasMessageContaining("ORO.puntosMaximos debe quedar vacío");
        assertThatThrownBy(() -> ReglasNiveles.validar(List.of(new UmbralNivel(Nivel.BRONCE, 0L, 499L),
                new UmbralNivel(Nivel.PLATA, 500L, 400L), new UmbralNivel(Nivel.ORO, 2000L, null))))
                .hasMessageContaining("PLATA.puntosMaximos debe ser mayor o igual que el mínimo");
    }

    private static ConfiguracionNiveles configuracion(List<UmbralNivel> umbrales) {
        return new ConfiguracionNiveles(1, 12, ReglasNiveles.validar(umbrales), null, "sistema", OffsetDateTime.now());
    }
}
