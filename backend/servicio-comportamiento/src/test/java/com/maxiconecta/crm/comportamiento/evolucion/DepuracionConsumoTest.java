package com.maxiconecta.crm.comportamiento.evolucion;

import com.maxiconecta.crm.comportamiento.compra.AgregadoConsumoCategorias.ConsumoEnRango;
import com.maxiconecta.crm.comportamiento.compra.LineaConsumoCategoria;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static com.maxiconecta.crm.comportamiento.evolucion.CalculoEvolucionConsumoTest.compra;
import static com.maxiconecta.crm.comportamiento.evolucion.CalculoEvolucionConsumoTest.linea;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-307 · Validación de la consistencia y la calidad de los datos de consumo (SCRUM-306,
 * docs/compra/evolucion-consumo-categoria.md#calidad-de-los-datos-scrum-306).
 */
class DepuracionConsumoTest {

    @Test
    void conDatosCoherentesNoCorrigeNadaEInformaLasComprasAnalizadasYLasAnuladas() {
        ConsumoEnRango consumo = new ConsumoEnRango(
                List.of(compra(1, "2026-07-10T10:00:00-04:00", "300.00"),
                        compra(2, "2026-08-10T10:00:00-04:00", "129.90")),
                List.of(linea(1, "Electrónica", 1, "300.00"), linea(1, "Accesorios", 2, "50.50"),
                        linea(2, "Hogar", 1, "89.90"), linea(2, "Limpieza", 4, "40.00")),
                List.of(linea(1, "Accesorios", 2, "50.50")), 1);

        DepuracionConsumo.Resultado resultado = DepuracionConsumo.depurar(consumo);

        assertThat(resultado.calidad()).isEqualTo(new CalidadDatos(2, 1, 0, 0, 0, 0, true));
        assertThat(resultado.consumo()).isEqualTo(consumo);
    }

    // --- Categorías no asignadas ---

    @Test
    void unaCategoriaNulaOEnBlancoSeAgrupaComoSinCategoria() {
        ConsumoEnRango consumo = new ConsumoEnRango(
                List.of(compra(1, "2026-07-10T10:00:00-04:00", "30.00")),
                List.of(linea(1, null, 1, "10.00"), linea(1, "  ", 1, "20.00")),
                List.of(), 0);

        DepuracionConsumo.Resultado resultado = DepuracionConsumo.depurar(consumo);

        assertThat(resultado.consumo().compradas()).extracting(LineaConsumoCategoria::categoria)
                .containsOnly(DepuracionConsumo.SIN_CATEGORIA);
        assertThat(resultado.calidad().registrosSinCategoria()).isEqualTo(2);
        assertThat(resultado.calidad().comprasConDesgloseInconsistente()).isZero();
        assertThat(resultado.calidad().consistente()).isFalse();

        SerieCategoria sinCategoria = calcular(resultado).series().get(0);
        assertThat(sinCategoria.categoria()).isEqualTo("Sin categoría");
        assertThat(sinCategoria.monto()).isEqualByComparingTo("30.00");
        assertThat(sinCategoria.compras()).isEqualTo(1);
    }

    @Test
    void quitaLosEspaciosALosLadosParaQueSeaLaMismaCategoria() {
        ConsumoEnRango consumo = new ConsumoEnRango(
                List.of(compra(1, "2026-07-10T10:00:00-04:00", "100.00")),
                List.of(linea(1, "Hogar ", 1, "60.00"), linea(1, "Hogar", 1, "40.00")),
                List.of(), 0);

        DepuracionConsumo.Resultado resultado = DepuracionConsumo.depurar(consumo);

        assertThat(resultado.calidad().consistente()).isTrue();
        assertThat(calcular(resultado).series()).singleElement()
                .satisfies(hogar -> {
                    assertThat(hogar.categoria()).isEqualTo("Hogar");
                    assertThat(hogar.monto()).isEqualByComparingTo("100.00");
                    assertThat(hogar.compras()).isEqualTo(1);
                });
    }

    // --- Datos faltantes ---

    @Test
    void unMontoFaltanteCuentaComoCeroYSeInforma() {
        ConsumoEnRango consumo = new ConsumoEnRango(
                List.of(compra(1, "2026-07-10T10:00:00-04:00", "100.00")),
                List.of(linea(1, "Hogar", 1, "100.00"), linea(1, "Limpieza", 2, null)),
                List.of(), 0);

        DepuracionConsumo.Resultado resultado = DepuracionConsumo.depurar(consumo);

        assertThat(resultado.consumo().compradas()).allMatch(l -> l.monto() != null);
        assertThat(resultado.calidad().registrosSinMonto()).isEqualTo(1);
        assertThat(resultado.calidad().consistente()).isFalse();
    }

    // --- Coherencia de devoluciones y desglose ---

    @Test
    void unaDevolucionMayorALoCompradoSeInformaYNoRestaMasDeLoComprado() {
        ConsumoEnRango consumo = new ConsumoEnRango(
                List.of(compra(1, "2026-07-10T10:00:00-04:00", "300.00")),
                List.of(linea(1, "Electrónica", 1, "300.00"), linea(1, "Accesorios", 1, "20.00")),
                List.of(linea(1, "Accesorios", 2, "40.00")), 0);

        DepuracionConsumo.Resultado resultado = DepuracionConsumo.depurar(consumo);

        assertThat(resultado.calidad().devolucionesInconsistentes()).isEqualTo(1);
        assertThat(calcular(resultado).series()).extracting(SerieCategoria::categoria).containsExactly("Electrónica");
    }

    @Test
    void unaDevolucionDeUnaCategoriaQueLaCompraNoTeniaSeInforma() {
        ConsumoEnRango consumo = new ConsumoEnRango(
                List.of(compra(1, "2026-07-10T10:00:00-04:00", "280.00")),
                List.of(linea(1, "Electrónica", 1, "300.00")),
                List.of(linea(1, "Juguetes", 1, "20.00")), 0);

        DepuracionConsumo.Resultado resultado = DepuracionConsumo.depurar(consumo);

        assertThat(resultado.calidad().devolucionesInconsistentes()).isEqualTo(1);
        assertThat(resultado.calidad().comprasConDesgloseInconsistente()).isEqualTo(1);
        assertThat(calcular(resultado).series()).extracting(SerieCategoria::categoria).containsExactly("Electrónica");
    }

    @Test
    void unaCompraCuyoDesgloseNoSumaSuMontoVigenteSeInforma() {
        ConsumoEnRango consumo = new ConsumoEnRango(
                List.of(compra(1, "2026-07-10T10:00:00-04:00", "150.00"),
                        compra(2, "2026-07-11T10:00:00-04:00", "75.00")),
                List.of(linea(1, "Hogar", 1, "100.00"), linea(2, "Hogar", 1, "75.00")),
                List.of(), 0);

        CalidadDatos calidad = DepuracionConsumo.depurar(consumo).calidad();

        assertThat(calidad.comprasConDesgloseInconsistente()).isEqualTo(1);
        assertThat(calidad.consistente()).isFalse();
    }

    @Test
    void unaCompraSinDesglosePorCategoriaSeInforma() {
        ConsumoEnRango consumo = new ConsumoEnRango(
                List.of(compra(1, "2026-07-10T10:00:00-04:00", "50.00")),
                List.of(), List.of(), 0);

        assertThat(DepuracionConsumo.depurar(consumo).calidad().comprasConDesgloseInconsistente()).isEqualTo(1);
    }

    private static CalculoEvolucionConsumo.Resultado calcular(DepuracionConsumo.Resultado depurado) {
        List<Periodo> periodos = Periodo.entre(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                TipoPeriodo.MENSUAL);
        return CalculoEvolucionConsumo.calcular(periodos, ZoneId.of("America/La_Paz"), depurado.consumo(), Set.of());
    }
}
