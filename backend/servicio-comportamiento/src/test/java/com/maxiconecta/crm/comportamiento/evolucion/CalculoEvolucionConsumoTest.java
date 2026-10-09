package com.maxiconecta.crm.comportamiento.evolucion;

import com.maxiconecta.crm.comportamiento.compra.AgregadoConsumoCategorias.CompraEnRango;
import com.maxiconecta.crm.comportamiento.compra.AgregadoConsumoCategorias.ConsumoEnRango;
import com.maxiconecta.crm.comportamiento.compra.LineaConsumoCategoria;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * SCRUM-307 · Agrupación del consumo por categoría y periodo sin base de datos
 * (docs/compra/evolucion-consumo-categoria.md).
 */
class CalculoEvolucionConsumoTest {

    private static final ZoneId LA_PAZ = ZoneId.of("America/La_Paz");
    private static final List<Periodo> JULIO_A_SEPTIEMBRE =
            Periodo.entre(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30), TipoPeriodo.MENSUAL);

    // --- Criterio 1: monto y cantidad de compras por categoría en cada periodo ---

    @Test
    void sumaElMontoYCuentaLasComprasDeCadaCategoriaEnCadaPeriodo() {
        ConsumoEnRango consumo = new ConsumoEnRango(
                List.of(compra(1, "2026-07-05T10:00:00-04:00", "100.00"),
                        compra(2, "2026-07-20T10:00:00-04:00", "50.00"),
                        compra(3, "2026-09-02T10:00:00-04:00", "350.00")),
                List.of(linea(1, "Hogar", 1, "100.00"),
                        linea(2, "Hogar", 2, "50.00"),
                        linea(3, "Hogar", 1, "50.00"),
                        linea(3, "Electrónica", 1, "300.00")),
                List.of(), 0);

        CalculoEvolucionConsumo.Resultado resultado = calcular(consumo, Set.of());

        SerieCategoria hogar = serie(resultado, "Hogar");
        assertThat(hogar.consumo()).extracting(ConsumoPeriodo::periodo, c -> c.monto().toPlainString(),
                        ConsumoPeriodo::compras, ConsumoPeriodo::unidades)
                .containsExactly(tuple("2026-07", "150.00", 2L, 3L),
                        tuple("2026-08", "0", 0L, 0L),
                        tuple("2026-09", "50.00", 1L, 1L));
        assertThat(hogar.monto()).isEqualByComparingTo("200.00");
        assertThat(hogar.compras()).isEqualTo(3);
        assertThat(serie(resultado, "Electrónica").consumo()).extracting(c -> c.monto().toPlainString())
                .containsExactly("0", "0", "300.00");
    }

    // --- Criterio 3: un periodo sin compras aparece en cero ---

    @Test
    void losPeriodosSinComprasDeUnaCategoriaAparecenEnCeroYNoSeOmiten() {
        ConsumoEnRango consumo = new ConsumoEnRango(
                List.of(compra(1, "2026-08-15T10:00:00-04:00", "40.00")),
                List.of(linea(1, "Limpieza", 4, "40.00")),
                List.of(), 0);

        SerieCategoria limpieza = serie(calcular(consumo, Set.of()), "Limpieza");

        assertThat(limpieza.consumo()).hasSize(3);
        assertThat(limpieza.consumo().get(0)).isEqualTo(ConsumoPeriodo.sinConsumo("2026-07"));
        assertThat(limpieza.consumo().get(2)).isEqualTo(ConsumoPeriodo.sinConsumo("2026-09"));
    }

    // --- Devoluciones parciales: se descuentan por categoría en el periodo de la compra ---

    @Test
    void descuentaLoDevueltoDeCadaCategoriaEnElPeriodoDeLaCompraOriginal() {
        ConsumoEnRango consumo = new ConsumoEnRango(
                List.of(compra(1, "2026-07-10T10:00:00-04:00", "325.25")),
                List.of(linea(1, "Electrónica", 1, "300.00"), linea(1, "Accesorios", 2, "50.50")),
                List.of(linea(1, "Accesorios", 1, "25.25")), 0);

        SerieCategoria accesorios = serie(calcular(consumo, Set.of()), "Accesorios");

        assertThat(accesorios.consumo().get(0).monto()).isEqualByComparingTo("25.25");
        assertThat(accesorios.consumo().get(0).unidades()).isEqualTo(1);
        assertThat(accesorios.consumo().get(0).compras()).isEqualTo(1);
    }

    @Test
    void unaCategoriaDevueltaPorCompletoDejaDeContarEnEsaCompra() {
        ConsumoEnRango consumo = new ConsumoEnRango(
                List.of(compra(1, "2026-07-10T10:00:00-04:00", "300.00")),
                List.of(linea(1, "Electrónica", 1, "300.00"), linea(1, "Accesorios", 2, "50.50")),
                List.of(linea(1, "Accesorios", 2, "50.50")), 0);

        CalculoEvolucionConsumo.Resultado resultado = calcular(consumo, Set.of());

        assertThat(resultado.series()).extracting(SerieCategoria::categoria).containsExactly("Electrónica");
        assertThat(resultado.categoriasDisponibles()).containsExactly("Electrónica");
    }

    // --- Criterio 2: filtro por categorías ---

    @Test
    void conFiltroSoloMuestraEsasCategoriasAunqueNoTenganConsumo() {
        ConsumoEnRango consumo = new ConsumoEnRango(
                List.of(compra(1, "2026-07-10T10:00:00-04:00", "389.90")),
                List.of(linea(1, "Electrónica", 1, "300.00"), linea(1, "Hogar", 1, "89.90")),
                List.of(), 0);

        CalculoEvolucionConsumo.Resultado resultado = calcular(consumo, Set.of("Hogar", "Juguetes"));

        assertThat(resultado.series()).extracting(SerieCategoria::categoria).containsExactly("Hogar", "Juguetes");
        assertThat(serie(resultado, "Juguetes").consumo()).allMatch(c -> c.monto().signum() == 0 && c.compras() == 0);
        assertThat(resultado.categoriasDisponibles()).containsExactly("Electrónica", "Hogar");
        assertThat(resultado.totales().get(0).monto()).isEqualByComparingTo("89.90");
    }

    @Test
    void elFiltroComparaElNombreExactoDeLaCategoria() {
        ConsumoEnRango consumo = new ConsumoEnRango(
                List.of(compra(1, "2026-07-10T10:00:00-04:00", "89.90")),
                List.of(linea(1, "Hogar", 1, "89.90")),
                List.of(), 0);

        SerieCategoria hogar = serie(calcular(consumo, Set.of("hogar")), "hogar");

        assertThat(hogar.monto()).isEqualByComparingTo("0");
    }

    // --- Periodos y zona horaria ---

    @Test
    void asignaCadaCompraAlPeriodoDeSuFechaEnLaZonaHorariaDelNegocio() {
        // 2026-10-01T01:30Z es el 30 de septiembre a las 21:30 en La Paz
        ConsumoEnRango consumo = new ConsumoEnRango(
                List.of(compra(1, "2026-10-01T01:30:00Z", "60.00")),
                List.of(linea(1, "Hogar", 1, "60.00")),
                List.of(), 0);

        SerieCategoria hogar = serie(calcular(consumo, Set.of()), "Hogar");

        assertThat(hogar.consumo().get(2).periodo()).isEqualTo("2026-09");
        assertThat(hogar.consumo().get(2).monto()).isEqualByComparingTo("60.00");
    }

    @Test
    void agrupaPorTrimestreConLosMismosDatos() {
        List<Periodo> trimestres = Periodo.entre(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 30),
                TipoPeriodo.TRIMESTRAL);
        ConsumoEnRango consumo = new ConsumoEnRango(
                List.of(compra(1, "2026-02-10T10:00:00-04:00", "30.00"),
                        compra(2, "2026-03-10T10:00:00-04:00", "20.00"),
                        compra(3, "2026-08-10T10:00:00-04:00", "10.00")),
                List.of(linea(1, "Hogar", 1, "30.00"), linea(2, "Hogar", 1, "20.00"), linea(3, "Hogar", 1, "10.00")),
                List.of(), 0);

        SerieCategoria hogar = serie(CalculoEvolucionConsumo.calcular(trimestres, LA_PAZ, consumo, Set.of()), "Hogar");

        assertThat(hogar.consumo()).extracting(ConsumoPeriodo::periodo, c -> c.monto().toPlainString(),
                        ConsumoPeriodo::compras)
                .containsExactly(tuple("2026-T1", "50.00", 2L), tuple("2026-T2", "0", 0L), tuple("2026-T3", "10.00", 1L));
    }

    // --- Orden y totales ---

    @Test
    void ordenaLasCategoriasDeMayorAMenorMontoYLuegoPorCompras() {
        ConsumoEnRango consumo = new ConsumoEnRango(
                List.of(compra(1, "2026-07-10T10:00:00-04:00", "120.00"),
                        compra(2, "2026-08-10T10:00:00-04:00", "70.00"),
                        compra(3, "2026-09-10T10:00:00-04:00", "50.00")),
                List.of(linea(1, "Accesorios", 1, "20.00"), linea(1, "Hogar", 1, "100.00"),
                        linea(2, "Accesorios", 1, "20.00"), linea(2, "Limpieza", 1, "50.00"),
                        linea(3, "Limpieza", 1, "50.00")),
                List.of(), 0);

        // Hogar y Limpieza suman 100.00, pero Limpieza aparece en dos compras
        assertThat(calcular(consumo, Set.of()).series()).extracting(SerieCategoria::categoria)
                .containsExactly("Limpieza", "Hogar", "Accesorios");
    }

    @Test
    void elTotalDeCadaPeriodoCuentaUnaSolaVezLaCompraConVariasCategorias() {
        ConsumoEnRango consumo = new ConsumoEnRango(
                List.of(compra(1, "2026-07-10T10:00:00-04:00", "389.90")),
                List.of(linea(1, "Electrónica", 1, "300.00"), linea(1, "Hogar", 1, "89.90")),
                List.of(), 0);

        List<TotalPeriodo> totales = calcular(consumo, Set.of()).totales();

        assertThat(totales).extracting(TotalPeriodo::periodo, t -> t.monto().toPlainString(), TotalPeriodo::compras)
                .containsExactly(tuple("2026-07", "389.90", 1L), tuple("2026-08", "0", 0L), tuple("2026-09", "0", 0L));
    }

    @Test
    void sinComprasNoHayCategoriasYLosTotalesVanEnCero() {
        CalculoEvolucionConsumo.Resultado resultado = calcular(ConsumoEnRango.VACIO, Set.of());

        assertThat(resultado.series()).isEmpty();
        assertThat(resultado.categoriasDisponibles()).isEmpty();
        assertThat(resultado.totales()).extracting(TotalPeriodo::compras).containsExactly(0L, 0L, 0L);
    }

    private static CalculoEvolucionConsumo.Resultado calcular(ConsumoEnRango consumo, Set<String> filtro) {
        return CalculoEvolucionConsumo.calcular(JULIO_A_SEPTIEMBRE, LA_PAZ, consumo, filtro);
    }

    private static SerieCategoria serie(CalculoEvolucionConsumo.Resultado resultado, String categoria) {
        return resultado.series().stream().filter(s -> s.categoria().equals(categoria)).findFirst().orElseThrow();
    }

    static CompraEnRango compra(long id, String fecha, String montoVigente) {
        return new CompraEnRango(id, OffsetDateTime.parse(fecha), new BigDecimal(montoVigente));
    }

    static LineaConsumoCategoria linea(long idCompra, String categoria, long unidades, String monto) {
        return new LineaConsumoCategoria(idCompra, categoria, unidades, monto == null ? null : new BigDecimal(monto));
    }
}
