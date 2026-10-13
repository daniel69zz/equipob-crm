package com.maxiconecta.crm.comportamiento.cambio;

import com.maxiconecta.crm.comportamiento.compra.ComprasPorPeriodoCliente;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCRUM-316, SCRUM-317, SCRUM-314, SCRUM-320 · Criterio de detección de cambios de comportamiento,
 * sin base de datos. Por defecto: ventana de 90 días, 3 periodos de línea base, 3 compras mínimas y
 * umbral de 50 %.
 */
class CriterioCambioComportamientoTest {

    private static final Instant REFERENCIA = Instant.parse("2026-10-10T12:00:00Z");
    private static final OffsetDateTime AHORA = REFERENCIA.atOffset(ZoneOffset.UTC);

    private final CriterioCambioComportamiento criterio = criterio("0.5");
    private final Ventanas ventanas = criterio.ventanas();

    @Test
    void elPeriodoActualSonLosUltimosNoventaDiasYLaLineaBaseLosTrescientosSetentaAnteriores() {
        assertThat(ventanas.fin()).isEqualTo(AHORA);
        assertThat(ventanas.inicioActual()).isEqualTo(AHORA.minusDays(90));
        assertThat(ventanas.inicioBase()).isEqualTo(AHORA.minusDays(360));
    }

    // --- Criterio 2: historial insuficiente ---

    @Test
    void conMenosComprasQueElMinimoQuedaSinLineaBase() {
        Evaluacion evaluacion = criterio.evaluar(compras(360, 2, "200", 0, "0"), ventanas);

        assertThat(evaluacion.estado()).isEqualTo(EstadoComportamiento.SIN_LINEA_BASE);
        assertThat(evaluacion.comprasLineaBase()).isEqualTo(2);
        assertThat(evaluacion.variaciones()).isEmpty();
    }

    @Test
    void sinUnPeriodoCompletoDeAntiguedadQuedaSinLineaBaseAunqueTengaCompras() {
        Evaluacion evaluacion = criterio.evaluar(compras(150, 8, "800", 0, "0"), ventanas);

        assertThat(evaluacion.estado()).isEqualTo(EstadoComportamiento.SIN_LINEA_BASE);
    }

    // --- Criterio 1: variaciones respecto a lo habitual ---

    @Test
    void unComportamientoParecidoAlHabitualEsEstable() {
        Evaluacion evaluacion = criterio.evaluar(compras(360, 6, "600", 2, "230"), ventanas);

        assertThat(evaluacion.estado()).isEqualTo(EstadoComportamiento.ESTABLE);
        assertThat(evaluacion.variaciones()).isEmpty();
    }

    @Test
    void unaCaidaDeFrecuenciaYMontoGeneraDosDisminuciones() {
        Evaluacion evaluacion = criterio.evaluar(compras(360, 12, "1200", 1, "100"), ventanas);

        assertThat(evaluacion.estado()).isEqualTo(EstadoComportamiento.CON_VARIACION);
        assertThat(evaluacion.variaciones()).hasSize(2);
        Variacion frecuencia = evaluacion.variaciones().get(0);
        assertThat(frecuencia.tipo()).isEqualTo(TipoVariacion.FRECUENCIA);
        assertThat(frecuencia.direccion()).isEqualTo(DireccionVariacion.DISMINUCION);
        assertThat(frecuencia.actual()).isEqualByComparingTo("1");
        assertThat(frecuencia.habitual()).isEqualByComparingTo("4");
        assertThat(frecuencia.relativa()).isEqualByComparingTo("-0.75");
        Variacion monto = evaluacion.variaciones().get(1);
        assertThat(monto.tipo()).isEqualTo(TipoVariacion.MONTO);
        assertThat(monto.direccion()).isEqualTo(DireccionVariacion.DISMINUCION);
        assertThat(monto.habitual()).isEqualByComparingTo("400");
        assertThat(monto.relativa()).isEqualByComparingTo("-0.75");
    }

    @Test
    void gastarMasConLaMismaFrecuenciaSoloGeneraUnAumentoDeMonto() {
        Evaluacion evaluacion = criterio.evaluar(compras(360, 6, "600", 2, "500"), ventanas);

        assertThat(evaluacion.variaciones()).singleElement().satisfies(variacion -> {
            assertThat(variacion.tipo()).isEqualTo(TipoVariacion.MONTO);
            assertThat(variacion.direccion()).isEqualTo(DireccionVariacion.AUMENTO);
            assertThat(variacion.relativa()).isEqualByComparingTo("1.5");
        });
    }

    @Test
    void noComprarNadaEnElPeriodoActualEsUnaDisminucionDelCienPorCiento() {
        Evaluacion evaluacion = criterio.evaluar(compras(360, 3, "300", 0, "0"), ventanas);

        assertThat(evaluacion.variaciones()).hasSize(2)
                .allSatisfy(variacion -> assertThat(variacion.relativa()).isEqualByComparingTo("-1"));
    }

    @Test
    void unaVariacionExactamenteEnElUmbralEsRelevante() {
        assertThat(criterio.variacion(TipoVariacion.FRECUENCIA, new BigDecimal("2"), new BigDecimal("4")))
                .hasValueSatisfying(v -> assertThat(v.relativa()).isEqualByComparingTo("-0.5"));
        assertThat(criterio.variacion(TipoVariacion.FRECUENCIA, new BigDecimal("6"), new BigDecimal("4")))
                .hasValueSatisfying(v -> assertThat(v.direccion()).isEqualTo(DireccionVariacion.AUMENTO));
        assertThat(criterio.variacion(TipoVariacion.FRECUENCIA, new BigDecimal("2.01"), new BigDecimal("4"))).isEmpty();
        assertThat(criterio.variacion(TipoVariacion.FRECUENCIA, new BigDecimal("5.99"), new BigDecimal("4"))).isEmpty();
    }

    @Test
    void unClienteQueEmpezoAComprarAMitadDeLaLineaBaseSeComparaConLoQueLlegoAObservarse() {
        // Primera compra 135 días antes del periodo actual (1,5 periodos): 3 compras equivalen a 2 por periodo.
        Evaluacion evaluacion = criterio.evaluar(compras(225, 3, "300", 2, "200"), ventanas);

        assertThat(evaluacion.estado()).isEqualTo(EstadoComportamiento.ESTABLE);
    }

    @Test
    void unUmbralMasAltoDetectaMenos() {
        CriterioCambioComportamiento estricto = criterio("0.8");

        Evaluacion evaluacion = estricto.evaluar(compras(360, 12, "1200", 1, "100"), estricto.ventanas());

        assertThat(evaluacion.estado()).isEqualTo(EstadoComportamiento.ESTABLE);
    }

    @Test
    void unaConfiguracionInvalidaImpideArrancar() {
        assertThatThrownBy(() -> criterio("0")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> criterio("1.5")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CriterioCambioComportamiento(0, 3, 3, new BigDecimal("0.5"), reloj()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CriterioCambioComportamiento(90, 3, 0, new BigDecimal("0.5"), reloj()))
                .isInstanceOf(IllegalStateException.class);
    }

    private static CriterioCambioComportamiento criterio(String umbral) {
        return new CriterioCambioComportamiento(90, 3, 3, new BigDecimal(umbral), reloj());
    }

    private static Clock reloj() {
        return Clock.fixed(REFERENCIA, ZoneOffset.UTC);
    }

    private static ComprasPorPeriodoCliente compras(int diasDesdePrimeraCompra, long comprasBase, String montoBase,
                                                    long comprasActual, String montoActual) {
        return new ComprasPorPeriodoCliente("CLI-1", AHORA.minusDays(diasDesdePrimeraCompra), comprasBase,
                new BigDecimal(montoBase), comprasActual, new BigDecimal(montoActual));
    }
}
