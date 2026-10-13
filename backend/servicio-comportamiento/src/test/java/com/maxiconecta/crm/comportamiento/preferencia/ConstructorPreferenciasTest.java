package com.maxiconecta.crm.comportamiento.preferencia;

import com.maxiconecta.crm.comportamiento.compra.AgregadoConsumoCategorias.CompraEnRango;
import com.maxiconecta.crm.comportamiento.compra.AgregadoConsumoCategorias.ConsumoEnRango;
import com.maxiconecta.crm.comportamiento.compra.LineaConsumoCategoria;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ConstructorPreferenciasTest {
    @Test
    void ordenaPorMontoLuegoComprasYLuegoNombreSinConfundirFrecuenciaConUnidades() {
        var resultado = ConstructorPreferencias.construir(consumo(List.of(
                linea(1, "Electrónica", 1, "300"), linea(2, "Hogar", 30, "50"),
                linea(3, "Hogar", 1, "50"), linea(4, "Deportes", 1, "100"),
                linea(5, "Alimentos", 10, "100")), List.of()));
        assertThat(resultado).extracting(c -> c.categoria()).containsExactly("Electrónica", "Hogar", "Alimentos", "Deportes");
        assertThat(resultado).extracting(c -> c.posicion()).containsExactly(1, 2, 3, 4);
        assertThat(resultado.get(1).compras()).isEqualTo(2);
    }

    @Test
    void unaCategoriaEsLaPreferenciaPrincipalYSumaLosItemsDeLaMismaCompraUnaVez() {
        var resultado = ConstructorPreferencias.construir(consumo(List.of(
                linea(1, " Hogar ", 2, "20"), linea(1, "Hogar", 1, "30")), List.of()));
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).posicion()).isEqualTo(1);
        assertThat(resultado.get(0).compras()).isEqualTo(1);
        assertThat(resultado.get(0).monto()).isEqualByComparingTo("50");
    }

    @Test
    void excluyeCategoriaNulaOVaciaSinPerderLasCategoriasValidas() {
        var resultado = ConstructorPreferencias.construir(consumo(List.of(
                linea(1, null, 1, "500"), linea(1, " ", 1, "300"), linea(1, "Hogar", 1, "20")), List.of()));
        assertThat(resultado).extracting(c -> c.categoria()).containsExactly("Hogar");
        assertThat(resultado.get(0).posicion()).isEqualTo(1);
    }

    @Test
    void unNombreRealSinCategoriaNoSeConfundeConUnDatoSinAsignar() {
        var resultado = ConstructorPreferencias.construir(consumo(List.of(
                linea(1, null, 1, "500"), linea(1, "Sin categoría", 1, "20")), List.of()));
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).monto()).isEqualByComparingTo("20");
    }

    @Test
    void sinComprasOConTodasLasCategoriasSinAsignarElPerfilEsVacio() {
        assertThat(ConstructorPreferencias.construir(ConsumoEnRango.VACIO)).isEmpty();
        assertThat(ConstructorPreferencias.construir(consumo(List.of(linea(1, null, 1, "30")), List.of()))).isEmpty();
    }

    @Test
    void descuentaDevolucionesYQuitaLaCategoriaDevueltaPorCompleto() {
        var resultado = ConstructorPreferencias.construir(consumo(List.of(
                linea(1, "Hogar", 2, "200"), linea(1, "Deportes", 1, "80"), linea(2, "Hogar", 1, "50")),
                List.of(linea(1, "Hogar", 1, "100"), linea(1, "Deportes", 1, "80"))));
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).monto()).isEqualByComparingTo("150");
        assertThat(resultado.get(0).compras()).isEqualTo(2);
        assertThat(resultado.get(0).unidades()).isEqualTo(2);
    }

    @Test
    void noUnificaMayusculasOTildesYDesempataDeFormaDeterminista() {
        var resultado = ConstructorPreferencias.construir(consumo(List.of(
                linea(1, "hogar", 1, "20"), linea(2, "Hogar", 1, "20")), List.of()));
        assertThat(resultado).extracting(c -> c.categoria()).containsExactly("Hogar", "hogar");
    }

    @Test
    void rechazaUnDesgloseQueNoCoincideConElMontoVigente() {
        var consumo = new ConsumoEnRango(List.of(new CompraEnRango(1, OffsetDateTime.now(), new BigDecimal("50"))),
                List.of(linea(1, "Hogar", 1, "20")), List.of(), 0);
        assertThatThrownBy(() -> ConstructorPreferencias.construir(consumo)).isInstanceOf(PreferenciasException.class);
    }

    @Test
    void rechazaDevolucionDeCategoriaAjenaAunqueElTotalCuadre() {
        var consumo = consumo(List.of(linea(1, "Hogar", 2, "100")), List.of(linea(1, "Deportes", 1, "10")));
        assertThatThrownBy(() -> ConstructorPreferencias.construir(consumo)).isInstanceOf(PreferenciasException.class);
    }

    @Test
    void rechazaLineasDeUnaCompraAjenaOMontosFaltantesONegativos() {
        var compra = new CompraEnRango(1, OffsetDateTime.now(), new BigDecimal("50"));
        for (var linea : List.of(linea(2, "Hogar", 1, "50"), linea(1, "Hogar", -1, "50"),
                new LineaConsumoCategoria(1, "Hogar", 1, null), linea(1, "Hogar", 1, "-50"))) {
            var consumo = new ConsumoEnRango(List.of(compra), List.of(linea), List.of(), 0);
            assertThatThrownBy(() -> ConstructorPreferencias.construir(consumo)).isInstanceOf(PreferenciasException.class);
        }
    }

    @Test
    void unaDevolucionExcesivaNoSeCompensaConDatosSinAsignar() {
        var consumo = consumo(List.of(linea(1, null, 1, "100"), linea(1, "Sin categoría", 1, "10")),
                List.of(linea(1, "Sin categoría", 1, "50")));
        assertThatThrownBy(() -> ConstructorPreferencias.construir(consumo)).isInstanceOf(PreferenciasException.class);
    }

    @Test
    void rechazaDevolverMasUnidadesAunqueElMontoSeaMenor() {
        var consumo = consumo(List.of(linea(1, "Hogar", 1, "100")), List.of(linea(1, "Hogar", 2, "20")));
        assertThatThrownBy(() -> ConstructorPreferencias.construir(consumo)).isInstanceOf(PreferenciasException.class);
    }

    static LineaConsumoCategoria linea(long compra, String categoria, long unidades, String monto) {
        return new LineaConsumoCategoria(compra, categoria, unidades, new BigDecimal(monto));
    }

    static ConsumoEnRango consumo(List<LineaConsumoCategoria> compradas, List<LineaConsumoCategoria> devueltas) {
        Map<Long, BigDecimal> totales = new LinkedHashMap<>();
        compradas.forEach(l -> totales.merge(l.idCompra(), l.monto(), BigDecimal::add));
        devueltas.forEach(l -> totales.merge(l.idCompra(), l.monto().negate(), BigDecimal::add));
        return new ConsumoEnRango(totales.entrySet().stream().map(e ->
                new CompraEnRango(e.getKey(), OffsetDateTime.parse("2026-01-01T00:00:00Z"), e.getValue())).toList(),
                compradas, devueltas, 0);
    }
}
