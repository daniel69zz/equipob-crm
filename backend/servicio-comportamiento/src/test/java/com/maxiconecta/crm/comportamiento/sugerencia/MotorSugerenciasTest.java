package com.maxiconecta.crm.comportamiento.sugerencia;

import com.maxiconecta.crm.comportamiento.sugerencia.AgregadoSegmento.CategoriaSegmento;
import com.maxiconecta.crm.comportamiento.sugerencia.AgregadoSegmento.ConsumoSegmento;
import com.maxiconecta.crm.comportamiento.sugerencia.MotorSugerencias.Sugerencia;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SCRUM-384: reglas del motor de sugerencias (docs/preferencias/sugerencia-categorias.md). */
class MotorSugerenciasTest {
    private static final ReglaSugerencias REGLA = new ReglaSugerencias(2, new BigDecimal("0.20"), 5);

    @Test
    void ejemploDelDocumentoExcluyeLoPropioYOrdenaPorAfinidad() {
        var segmento = new ConsumoSegmento(4, List.of(categoria("Hogar", 4, "900"),
                categoria("Electrónica", 3, "4200"), categoria("Deportes", 2, "380"), categoria("Juguetes", 1, "50")));

        List<Sugerencia> sugerencias = MotorSugerencias.sugerir(List.of("Hogar"), segmento, REGLA);

        assertThat(sugerencias).extracting(Sugerencia::categoria).containsExactly("Electrónica", "Deportes");
        assertThat(sugerencias).extracting(Sugerencia::posicion).containsExactly(1, 2);
        assertThat(sugerencias.get(0).afinidad()).isEqualByComparingTo("0.75");
        assertThat(sugerencias.get(0).clientesQueCompran()).isEqualTo(3);
        assertThat(sugerencias.get(1).montoSegmento()).isEqualByComparingTo("380");
    }

    @Test
    void unaCategoriaQueElClienteYaCompraNoSeSugiereAunqueCambienMayusculasOEspacios() {
        var segmento = new ConsumoSegmento(3, List.of(categoria("Hogar", 3, "100"), categoria("Libros", 2, "50")));

        assertThat(MotorSugerencias.sugerir(List.of(" hogar "), segmento, REGLA))
                .extracting(Sugerencia::categoria).containsExactly("Libros");
    }

    @Test
    void aIgualAfinidadDesempataPorMontoDelSegmentoYLuegoPorNombre() {
        var segmento = new ConsumoSegmento(4, List.of(categoria("Música", 2, "100"), categoria("Cine", 2, "100"),
                categoria("Viajes", 2, "900")));

        assertThat(MotorSugerencias.sugerir(List.of(), segmento, REGLA))
                .extracting(Sugerencia::categoria).containsExactly("Viajes", "Cine", "Música");
    }

    @Test
    void respetaElMinimoDeClientesYLaAfinidadMinima() {
        var regla = new ReglaSugerencias(2, new BigDecimal("0.30"), 5);
        var segmento = new ConsumoSegmento(10, List.of(categoria("Juguetes", 1, "999"),
                categoria("Libros", 2, "10"), categoria("Cine", 3, "10")));

        assertThat(MotorSugerencias.sugerir(List.of(), segmento, regla))
                .extracting(Sugerencia::categoria).containsExactly("Cine");
    }

    @Test
    void limitaLaCantidadDeSugerencias() {
        var segmento = new ConsumoSegmento(3, List.of(categoria("A", 3, "1"), categoria("B", 3, "1"),
                categoria("C", 2, "1")));

        assertThat(MotorSugerencias.sugerir(List.of(), segmento, new ReglaSugerencias(1, BigDecimal.ZERO, 2)))
                .extracting(Sugerencia::categoria).containsExactly("A", "B");
    }

    @Test
    void sinCompanerosConPreferenciasNoSugiereNada() {
        assertThat(MotorSugerencias.sugerir(List.of("Hogar"), new ConsumoSegmento(0, List.of()), REGLA)).isEmpty();
    }

    @Test
    void laAfinidadSeRedondeaACuatroDecimales() {
        var segmento = new ConsumoSegmento(3, List.of(categoria("Libros", 2, "10")));

        assertThat(MotorSugerencias.sugerir(List.of(), segmento, REGLA).get(0).afinidad()).isEqualTo("0.6667");
    }

    @Test
    void rechazaUmbralesInvalidos() {
        assertThatThrownBy(() -> new ReglaSugerencias(0, new BigDecimal("0.2"), 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReglaSugerencias(2, new BigDecimal("1.5"), 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReglaSugerencias(2, new BigDecimal("-0.1"), 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReglaSugerencias(2, new BigDecimal("0.2"), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static CategoriaSegmento categoria(String nombre, int clientes, String monto) {
        return new CategoriaSegmento(nombre, clientes, clientes, new BigDecimal(monto));
    }
}
