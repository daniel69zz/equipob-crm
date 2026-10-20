package com.maxiconecta.crm.comportamiento.riesgo;

import com.maxiconecta.crm.comportamiento.consumo.CriterioNivelConsumo;
import com.maxiconecta.crm.comportamiento.consumo.NivelConsumo;
import com.maxiconecta.crm.comportamiento.frecuencia.ClasificacionFrecuencia;
import com.maxiconecta.crm.comportamiento.frecuencia.CriterioClasificacionFrecuencia;
import com.maxiconecta.crm.comportamiento.inactividad.CriterioInactividad;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCRUM-323 · Fórmula del riesgo de abandono (docs/compra/riesgo-abandono.md) con los casos de la
 * tabla de ejemplos: cliente activo, en descenso (con y sin caída detectada) e inactivo, los valores
 * límite de cada umbral y la configuración inválida. Valores por defecto: pesos 50/25/25,
 * penalización 15, umbrales 35 y 65, inactividad a los 90 días, habitual desde 4 compras y frecuente
 * desde 10, consumo medio desde 500 y alto desde 5000.
 */
class CriterioRiesgoAbandonoTest {

    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2026-10-20T12:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA.toInstant(), ZoneOffset.UTC);

    private final CriterioRiesgoAbandono criterio = criterio("50", "25", "25", "15", "35", "65");

    // --- Criterio 1: un puntaje y un nivel según la fórmula ---

    @Test
    void unClienteActivoFrecuenteYDeAltoValorTieneRiesgoBajo() {
        EvaluacionRiesgo evaluacion = evaluar(12, "6000.00", 3, false);

        assertThat(evaluacion.puntaje()).isEqualByComparingTo("1.67");
        assertThat(evaluacion.nivel()).isEqualTo(NivelRiesgo.BAJO);
        assertThat(evaluacion.diasSinComprar()).isEqualTo(3);
        assertThat(evaluacion.clasificacionFrecuencia()).isEqualTo(ClasificacionFrecuencia.FRECUENTE);
        assertThat(evaluacion.nivelConsumo()).isEqualTo(NivelConsumo.ALTO);
        assertThat(evaluacion.puntosRecencia()).isEqualByComparingTo("1.67");
        assertThat(evaluacion.puntosFrecuencia()).isEqualByComparingTo("0");
        assertThat(evaluacion.puntosValor()).isEqualByComparingTo("0");
        assertThat(evaluacion.puntosCambio()).isEqualByComparingTo("0");
        assertThat(evaluacion.inactivo()).isFalse();
    }

    @Test
    void unClienteEnDescensoTieneRiesgoMedio() {
        EvaluacionRiesgo evaluacion = evaluar(5, "1500.00", 50, false);

        assertThat(evaluacion.puntosRecencia()).isEqualByComparingTo("27.78");
        assertThat(evaluacion.puntosFrecuencia()).isEqualByComparingTo("12.50");
        assertThat(evaluacion.puntosValor()).isEqualByComparingTo("12.50");
        assertThat(evaluacion.puntaje()).isEqualByComparingTo("52.78");
        assertThat(evaluacion.nivel()).isEqualTo(NivelRiesgo.MEDIO);
    }

    // --- Criterio 2: una caída de comportamiento detectada sube el riesgo ---

    @Test
    void unaCaidaDetectadaSumaLaPenalizacionYLlevaAlClienteEnDescensoARiesgoAlto() {
        EvaluacionRiesgo evaluacion = evaluar(5, "1500.00", 50, true);

        assertThat(evaluacion.puntosCambio()).isEqualByComparingTo("15");
        assertThat(evaluacion.puntaje()).isEqualByComparingTo("67.78");
        assertThat(evaluacion.nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(evaluacion.cambioDetectado()).isTrue();
    }

    // --- Criterio 3: los clientes inactivos quedan en riesgo alto ---

    @Test
    void unClienteInactivoOcasionalYDeBajoValorTieneElPuntajeMaximo() {
        EvaluacionRiesgo evaluacion = evaluar(2, "300.00", 120, false);

        assertThat(evaluacion.puntaje()).isEqualByComparingTo("100");
        assertThat(evaluacion.nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(evaluacion.inactivo()).isTrue();
        assertThat(evaluacion.clasificacionFrecuencia()).isEqualTo(ClasificacionFrecuencia.OCASIONAL);
        assertThat(evaluacion.nivelConsumo()).isEqualTo(NivelConsumo.BAJO);
    }

    @Test
    void unClienteInactivoQueFueFrecuenteYDeAltoValorEsRiesgoAltoPorLaReglaDeInactividad() {
        EvaluacionRiesgo evaluacion = evaluar(12, "6000.00", 120, false);

        assertThat(evaluacion.puntaje()).isEqualByComparingTo("50");
        assertThat(evaluacion.nivel()).isEqualTo(NivelRiesgo.ALTO);
        assertThat(evaluacion.inactivo()).isTrue();
    }

    @Test
    void justoALosNoventaDiasLaRecenciaEsMaximaPeroTodaviaNoEsInactivo() {
        EvaluacionRiesgo evaluacion = evaluar(12, "6000.00", 90, false);

        assertThat(evaluacion.puntosRecencia()).isEqualByComparingTo("50");
        assertThat(evaluacion.inactivo()).isFalse();
        assertThat(evaluacion.nivel()).isEqualTo(NivelRiesgo.MEDIO);
    }

    // --- Valores límite ---

    @Test
    void unPuntajeExactamenteEnUnUmbralPerteneceAlNivelSuperior() {
        assertThat(criterio.nivel(new BigDecimal("34.99"), false)).isEqualTo(NivelRiesgo.BAJO);
        assertThat(criterio.nivel(new BigDecimal("35"), false)).isEqualTo(NivelRiesgo.MEDIO);
        assertThat(criterio.nivel(new BigDecimal("64.99"), false)).isEqualTo(NivelRiesgo.MEDIO);
        assertThat(criterio.nivel(new BigDecimal("65"), false)).isEqualTo(NivelRiesgo.ALTO);
        assertThat(criterio.nivel(BigDecimal.ZERO, true)).isEqualTo(NivelRiesgo.ALTO);
    }

    @Test
    void elPuntajeNoSuperaCien() {
        EvaluacionRiesgo evaluacion = evaluar(1, "100.00", 200, true);

        assertThat(evaluacion.puntaje()).isEqualByComparingTo("100");
        assertThat(evaluacion.puntosRecencia().add(evaluacion.puntosFrecuencia()).add(evaluacion.puntosValor())
                .add(evaluacion.puntosCambio())).isEqualByComparingTo("115");
    }

    @Test
    void unaCompraConFechaFuturaCuentaComoDeHoy() {
        EvaluacionRiesgo evaluacion = criterio.evaluar(new DatosRiesgo(12, new BigDecimal("6000.00"),
                AHORA.plusDays(2), false), AHORA);

        assertThat(evaluacion.diasSinComprar()).isZero();
        assertThat(evaluacion.puntosRecencia()).isEqualByComparingTo("0");
    }

    @Test
    void sinComprasVigentesNoHayRiesgoQueCalcular() {
        assertThatThrownBy(() -> criterio.evaluar(new DatosRiesgo(0, BigDecimal.ZERO, null, false), AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Configuración ---

    @Test
    void otrosPesosCambianLaPonderacion() {
        CriterioRiesgoAbandono soloRecencia = criterio("100", "0", "0", "0", "35", "65");

        EvaluacionRiesgo evaluacion = soloRecencia.evaluar(new DatosRiesgo(2, new BigDecimal("300.00"),
                AHORA.minusDays(45), true), AHORA);

        assertThat(evaluacion.puntaje()).isEqualByComparingTo("50");
        assertThat(evaluacion.nivel()).isEqualTo(NivelRiesgo.MEDIO);
    }

    @Test
    void unaConfiguracionInvalidaImpideArrancar() {
        assertThatThrownBy(() -> criterio("50", "25", "20", "15", "35", "65"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("deben sumar 100");
        assertThatThrownBy(() -> criterio("50", "25", "25", "120", "35", "65"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("penalización");
        assertThatThrownBy(() -> criterio("50", "25", "25", "15", "65", "35"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("umbrales");
        assertThatThrownBy(() -> criterio("50", "25", "25", "15", "35", "120"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("umbrales");
    }

    private EvaluacionRiesgo evaluar(long compras, String valor, int diasSinComprar, boolean disminucion) {
        return criterio.evaluar(new DatosRiesgo(compras, new BigDecimal(valor), AHORA.minusDays(diasSinComprar),
                disminucion), AHORA);
    }

    private static CriterioRiesgoAbandono criterio(String recencia, String frecuencia, String valor, String cambio,
                                                   String medio, String alto) {
        return new CriterioRiesgoAbandono(new BigDecimal(recencia), new BigDecimal(frecuencia), new BigDecimal(valor),
                new BigDecimal(cambio), new BigDecimal(medio), new BigDecimal(alto), new CriterioInactividad(90, RELOJ),
                new CriterioClasificacionFrecuencia(4, 10),
                new CriterioNivelConsumo(new BigDecimal("500"), new BigDecimal("5000")));
    }
}
