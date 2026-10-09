package com.maxiconecta.crm.comportamiento.evolucion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-307 · Detalle comparativo entre periodos y tendencia de cada categoría (SCRUM-303,
 * docs/compra/evolucion-consumo-categoria.md#comparación-entre-periodos-scrum-303).
 */
class ComparacionPeriodosTest {

    @Test
    void comparaCadaPeriodoConElAnteriorYConElPrimeroDelRango() {
        SerieComparada comparada = comparar(serie("100.00", "150.00", "0"), 0);

        List<PuntoEvolucion> evolucion = comparada.evolucion();
        assertThat(evolucion.get(0).variacionAnterior()).isNull();
        assertVariacion(evolucion.get(1).variacionAnterior(), "50.00", "50.00");
        assertVariacion(evolucion.get(2).variacionAnterior(), "-150.00", "-100.00");

        assertVariacion(evolucion.get(0).variacionBase(), "0", "0.00");
        assertVariacion(evolucion.get(1).variacionBase(), "50.00", "50.00");
        assertVariacion(evolucion.get(2).variacionBase(), "-100.00", "-100.00");
    }

    @Test
    void comparaContraElPeriodoBaseElegido() {
        SerieComparada comparada = comparar(serie("100.00", "200.00", "300.00"), 1);

        assertVariacion(comparada.evolucion().get(0).variacionBase(), "-100.00", "-50.00");
        assertVariacion(comparada.evolucion().get(2).variacionBase(), "100.00", "50.00");
    }

    @Test
    void sinMontoDeReferenciaElPorcentajeEsNulo() {
        SerieComparada comparada = comparar(serie("0", "80.00"), 0);

        assertVariacion(comparada.evolucion().get(1).variacionAnterior(), "80.00", null);
        assertVariacion(comparada.evolucion().get(1).variacionBase(), "80.00", null);
    }

    @Test
    void conservaElConsumoYLosTotalesDeLaSerie() {
        SerieComparada comparada = comparar(serie("10.00", "20.00"), 0);

        assertThat(comparada.categoria()).isEqualTo("Hogar");
        assertThat(comparada.monto()).isEqualByComparingTo("30.00");
        assertThat(comparada.evolucion()).extracting(PuntoEvolucion::periodo).containsExactly("P1", "P2");
        assertThat(comparada.evolucion().get(1).compras()).isEqualTo(1);
    }

    // --- Tendencia: primera mitad contra segunda mitad de los periodos completos ---

    @Test
    void creceSiLaSegundaMitadSuperaALaPrimeraEnMasDeDiezPorCiento() {
        assertThat(tendencia("100.00", "100.00", "150.00", "100.00")).isEqualTo(Tendencia.CRECE);
        // Justo 10 % no alcanza para considerarlo crecimiento
        assertThat(tendencia("100.00", "100.00", "120.00", "100.00")).isEqualTo(Tendencia.ESTABLE);
    }

    @Test
    void creceSiEmpiezaAComprarEnLaSegundaMitad() {
        assertThat(tendencia("0", "0", "50.00", "0")).isEqualTo(Tendencia.CRECE);
    }

    @Test
    void decreceSiLaSegundaMitadQuedaMasDeDiezPorCientoPorDebajo() {
        assertThat(tendencia("100.00", "100.00", "50.00", "100.00")).isEqualTo(Tendencia.DECRECE);
    }

    @Test
    void esEstableSiLaDiferenciaNoSuperaElDiezPorCiento() {
        assertThat(tendencia("100.00", "100.00", "110.00", "100.00")).isEqualTo(Tendencia.ESTABLE);
        assertThat(tendencia("100.00", "100.00", "90.00", "100.00")).isEqualTo(Tendencia.ESTABLE);
    }

    @Test
    void dejoDeComprarSiNoHuboConsumoEnLaSegundaMitad() {
        assertThat(tendencia("100.00", "40.00", "0", "0")).isEqualTo(Tendencia.DEJO_DE_COMPRAR);
    }

    @Test
    void conUnNumeroImparDePeriodosElCentralNoEntraEnNingunaMitad() {
        // Solo compró en el periodo central: no hay consumo en la segunda mitad
        assertThat(tendencia("0", "100.00", "0")).isEqualTo(Tendencia.DEJO_DE_COMPRAR);
        assertThat(tendencia("100.00", "500.00", "100.00")).isEqualTo(Tendencia.ESTABLE);
    }

    @Test
    void sinConsumoEnTodoElRangoNoHayTendencia() {
        assertThat(tendencia("0", "0", "0")).isEqualTo(Tendencia.SIN_CONSUMO);
    }

    @Test
    void conMenosDeDosPeriodosCompletosNoHayConQueComparar() {
        assertThat(tendencia("100.00")).isEqualTo(Tendencia.SIN_COMPARACION);
        assertThat(tendenciaConUltimoParcial("100.00", "0")).isEqualTo(Tendencia.SIN_COMPARACION);
    }

    // --- El periodo parcial final (el mes en curso) no entra en las mitades ---

    @Test
    void unClienteQueCompraLoMismoCadaMesSigueEstableAunqueElMesEnCursoVayaEnCero() {
        assertThat(tendenciaConUltimoParcial("50.00", "50.00", "50.00", "50.00", "0")).isEqualTo(Tendencia.ESTABLE);
        assertThat(tendenciaConUltimoParcial("50.00", "50.00", "50.00", "50.00", "50.00")).isEqualTo(Tendencia.ESTABLE);
    }

    @Test
    void quienEmpiezaAComprarEnElMesEnCursoCrece() {
        assertThat(tendenciaConUltimoParcial("0", "0", "0", "0", "80.00")).isEqualTo(Tendencia.CRECE);
    }

    @Test
    void quienVuelveAComprarEnElMesEnCursoNoQuedaComoPerdido() {
        assertThat(tendenciaConUltimoParcial("100.00", "100.00", "0", "0", "30.00")).isEqualTo(Tendencia.DECRECE);
        assertThat(tendenciaConUltimoParcial("100.00", "100.00", "0", "0", "0")).isEqualTo(Tendencia.DEJO_DE_COMPRAR);
    }

    private static Tendencia tendencia(String... montos) {
        return comparar(serie(montos), 0).tendencia();
    }

    private static Tendencia tendenciaConUltimoParcial(String... montos) {
        List<Periodo> periodos = new ArrayList<>(periodosCompletos(montos.length - 1));
        LocalDate inicio = LocalDate.of(2026, 1, 1).plusMonths(montos.length - 1);
        periodos.add(new Periodo("P" + montos.length, inicio, inicio.plusDays(7), true));
        return ComparacionPeriodos.comparar(serie(montos), 0, periodos).tendencia();
    }

    private static SerieComparada comparar(SerieCategoria serie, int indiceBase) {
        return ComparacionPeriodos.comparar(serie, indiceBase, periodosCompletos(serie.consumo().size()));
    }

    /** Meses completos P1, P2…, desde enero de 2026. */
    private static List<Periodo> periodosCompletos(int cantidad) {
        List<Periodo> periodos = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            LocalDate inicio = LocalDate.of(2026, 1, 1).plusMonths(i);
            periodos.add(new Periodo("P" + (i + 1), inicio, inicio.plusMonths(1).minusDays(1), false));
        }
        return periodos;
    }

    private static void assertVariacion(Variacion variacion, String monto, String porcentaje) {
        assertThat(variacion.monto()).isEqualByComparingTo(monto);
        if (porcentaje == null) {
            assertThat(variacion.porcentaje()).isNull();
        } else {
            assertThat(variacion.porcentaje()).isEqualByComparingTo(porcentaje);
        }
    }

    /** Serie de Hogar con un periodo por monto, P1, P2…; cada periodo con monto tiene una compra. */
    private static SerieCategoria serie(String... montos) {
        List<ConsumoPeriodo> consumo = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        long compras = 0;
        for (int i = 0; i < montos.length; i++) {
            BigDecimal monto = new BigDecimal(montos[i]);
            long comprasPeriodo = monto.signum() > 0 ? 1 : 0;
            consumo.add(new ConsumoPeriodo("P" + (i + 1), monto, comprasPeriodo, comprasPeriodo));
            total = total.add(monto);
            compras += comprasPeriodo;
        }
        return new SerieCategoria("Hogar", total, compras, compras, consumo);
    }
}
