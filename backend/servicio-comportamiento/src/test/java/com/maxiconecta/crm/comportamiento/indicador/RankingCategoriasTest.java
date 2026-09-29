package com.maxiconecta.crm.comportamiento.indicador;

import com.maxiconecta.crm.comportamiento.compra.LineaCategoria;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-212 · Agrupación y orden de las categorías más consumidas (docs/compra/categorias-mas-consumidas.md).
 */
class RankingCategoriasTest {

    private static LineaCategoria linea(long compra, String categoria, long unidades, String monto) {
        return new LineaCategoria(compra, categoria, unidades, monto == null ? null : new BigDecimal(monto));
    }

    private static List<CategoriaConsumida> ranking(List<LineaCategoria> compradas, LineaCategoria... devueltas) {
        return RankingCategorias.calcular(compradas, List.of(devueltas));
    }

    @Test
    void sinComprasElRankingVieneVacio() {
        assertThat(RankingCategorias.calcular(List.of(), List.of())).isEmpty();
    }

    @Test
    void acumulaComprasUnidadesYMontoDeCadaCategoriaYOrdenaPorMonto() {
        List<CategoriaConsumida> resultado = ranking(List.of(
                linea(1, "Hogar", 1, "89.90"),
                linea(1, "Limpieza", 4, "40.00"),
                linea(2, "Hogar", 2, "60.10"),
                linea(3, "Electrónica", 1, "300.00")));

        assertThat(resultado).extracting(CategoriaConsumida::categoria)
                .containsExactly("Electrónica", "Hogar", "Limpieza");
        CategoriaConsumida hogar = resultado.get(1);
        assertThat(hogar.compras()).isEqualTo(2);
        assertThat(hogar.unidades()).isEqualTo(3);
        assertThat(hogar.monto()).isEqualByComparingTo("150.00");
        assertThat(hogar.sinCategoria()).isFalse();
    }

    @Test
    void unaCategoriaConMasComprasPeroMenosMontoQuedaDespues() {
        List<CategoriaConsumida> resultado = ranking(List.of(
                linea(1, "Limpieza", 1, "10.00"),
                linea(2, "Limpieza", 1, "10.00"),
                linea(3, "Limpieza", 1, "10.00"),
                linea(4, "Hogar", 1, "100.00")));

        assertThat(resultado).extracting(CategoriaConsumida::categoria).containsExactly("Hogar", "Limpieza");
    }

    @Test
    void anteUnEmpateDeMontoDesempataLaFrecuenciaYLuegoElNombre() {
        List<CategoriaConsumida> resultado = ranking(List.of(
                linea(1, "Zapatos", 1, "100.00"),
                linea(2, "Bebidas", 1, "50.00"),
                linea(3, "Bebidas", 1, "50.00"),
                linea(4, "Alimentos", 1, "100.00"),
                linea(5, "alimentos ", 1, "0.01")));

        // Bebidas y Zapatos empatan en 100.00 pero Bebidas está en dos compras; Alimentos (100.00, una compra)
        // empata con Zapatos y va antes por nombre. «alimentos » es otra categoría, aunque parecida.
        assertThat(resultado).extracting(CategoriaConsumida::categoria)
                .containsExactly("Bebidas", "Alimentos", "Zapatos", "alimentos");
    }

    @Test
    void unaDevolucionDescuentaLasUnidadesYElMontoDeSuCategoria() {
        List<CategoriaConsumida> resultado = ranking(
                List.of(linea(1, "Accesorios", 2, "50.50"), linea(1, "Electrónica", 1, "300.00")),
                linea(1, "Accesorios", 1, "25.25"));

        assertThat(resultado).extracting(CategoriaConsumida::categoria).containsExactly("Electrónica", "Accesorios");
        CategoriaConsumida accesorios = resultado.get(1);
        assertThat(accesorios.compras()).isEqualTo(1);
        assertThat(accesorios.unidades()).isEqualTo(1);
        assertThat(accesorios.monto()).isEqualByComparingTo("25.25");
    }

    @Test
    void unaCategoriaDevueltaPorCompletoDejaDeContarEnEsaCompraPeroNoEnLasOtras() {
        List<CategoriaConsumida> resultado = ranking(
                List.of(linea(1, "Accesorios", 2, "50.50"), linea(2, "Accesorios", 1, "20.00")),
                linea(1, "Accesorios", 2, "50.50"));

        assertThat(resultado).singleElement().satisfies(accesorios -> {
            assertThat(accesorios.compras()).isEqualTo(1);
            assertThat(accesorios.unidades()).isEqualTo(1);
            assertThat(accesorios.monto()).isEqualByComparingTo("20.00");
        });
    }

    @Test
    void unaCategoriaDevueltaPorCompletoNoApareceEnElRanking() {
        assertThat(ranking(List.of(linea(1, "Accesorios", 2, "50.50")), linea(1, "Accesorios", 2, "50.50")))
                .isEmpty();
    }

    @Test
    void unaDevolucionQueSuperaLoCompradoNoDejaValoresNegativos() {
        List<CategoriaConsumida> resultado = ranking(
                List.of(linea(1, "Hogar", 1, "10.00"), linea(2, "Limpieza", 1, "5.00")),
                linea(2, "Limpieza", 3, "8.00"));

        assertThat(resultado).singleElement().satisfies(hogar -> assertThat(hogar.categoria()).isEqualTo("Hogar"));
    }

    @Test
    void unaCategoriaNulaOEnBlancoSeAgrupaComoSinCategoriaYNoRompeElCalculo() {
        List<CategoriaConsumida> resultado = ranking(List.of(
                linea(1, null, 1, "20.00"),
                linea(2, "  ", 2, "30.00"),
                linea(3, "Hogar", 1, "10.00")));

        assertThat(resultado).extracting(CategoriaConsumida::categoria)
                .containsExactly(RankingCategorias.SIN_CATEGORIA, "Hogar");
        CategoriaConsumida sinCategoria = resultado.get(0);
        assertThat(sinCategoria.sinCategoria()).isTrue();
        assertThat(sinCategoria.compras()).isEqualTo(2);
        assertThat(sinCategoria.unidades()).isEqualTo(3);
        assertThat(sinCategoria.monto()).isEqualByComparingTo("50.00");
        assertThat(resultado.get(1).sinCategoria()).isFalse();
    }

    @Test
    void ignoraLosEspaciosALosLadosDelNombreDeLaCategoria() {
        List<CategoriaConsumida> resultado = ranking(List.of(linea(1, " Hogar", 1, "10.00"), linea(2, "Hogar ", 1, "5.00")));

        assertThat(resultado).singleElement().satisfies(hogar -> {
            assertThat(hogar.categoria()).isEqualTo("Hogar");
            assertThat(hogar.compras()).isEqualTo(2);
        });
    }

    @Test
    void unMontoNuloCuentaComoCero() {
        List<CategoriaConsumida> resultado = ranking(List.of(linea(1, "Hogar", 1, null), linea(2, "Limpieza", 1, "5.00")));

        assertThat(resultado).extracting(CategoriaConsumida::categoria).containsExactly("Limpieza", "Hogar");
        assertThat(resultado.get(1).monto()).isEqualByComparingTo("0");
    }

    @Test
    void laSumaDeLosMontosDeLasCategoriasEsLoVigenteDelCliente() {
        List<CategoriaConsumida> resultado = ranking(
                List.of(linea(1, "A", 2, "50.50"), linea(1, "B", 1, "300.00"), linea(2, "A", 1, "10.00")),
                linea(1, "A", 1, "25.25"));

        BigDecimal suma = resultado.stream().map(CategoriaConsumida::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(suma).isEqualByComparingTo("335.25");
    }
}
