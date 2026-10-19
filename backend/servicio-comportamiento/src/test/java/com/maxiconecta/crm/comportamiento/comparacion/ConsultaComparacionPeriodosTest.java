package com.maxiconecta.crm.comportamiento.comparacion;

import com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException;
import com.maxiconecta.crm.comportamiento.comun.ServicioNoDisponibleException;
import com.maxiconecta.crm.comportamiento.compra.Identificador;
import com.maxiconecta.crm.comportamiento.compra.ResumenCompras;
import com.maxiconecta.crm.comportamiento.periodo.AgregadoConsumoPeriodo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SCRUM-351, SCRUM-353, SCRUM-357, SCRUM-358 · Comparación por cliente y por segmento, sin base de
 * datos.
 */
class ConsultaComparacionPeriodosTest {

    private static final OffsetDateTime INICIO_A = OffsetDateTime.parse("2026-08-01T00:00:00-04:00");
    private static final OffsetDateTime FIN_A = OffsetDateTime.parse("2026-09-01T00:00:00-04:00");
    private static final OffsetDateTime INICIO_B = OffsetDateTime.parse("2026-09-01T00:00:00-04:00");
    private static final OffsetDateTime FIN_B = OffsetDateTime.parse("2026-10-01T00:00:00-04:00");

    private final AgregadoConsumoPeriodo agregado = mock(AgregadoConsumoPeriodo.class);
    private final ClientesDelSegmento clientesDelSegmento = mock(ClientesDelSegmento.class);
    private final ConsultaComparacionPeriodos consulta = new ConsultaComparacionPeriodos(agregado,
            clientesDelSegmento, ZoneId.of("America/La_Paz"));
    private final PeriodosAComparar periodos = PeriodosAComparar.de(LocalDate.parse("2026-08-01"),
            LocalDate.parse("2026-08-31"), LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"));

    @Test
    void comparaLaFrecuenciaYElValorDeUnClienteEntreDosPeriodos() {
        List<Identificador> ids = List.of(new Identificador("CLI-5521"));
        when(agregado.resumir(ids, INICIO_A, FIN_A)).thenReturn(new ResumenCompras(2, new BigDecimal("480.50")));
        when(agregado.resumir(ids, INICIO_B, FIN_B)).thenReturn(new ResumenCompras(5, new BigDecimal("900.00")));

        ResultadoComparacion resultado = consulta.compararCliente(ids, periodos);

        assertThat(resultado.alcance().tipo()).isEqualTo("CLIENTE");
        assertThat(resultado.alcance().segmento()).isNull();
        assertThat(resultado.zonaHoraria()).isEqualTo("America/La_Paz");
        assertThat(resultado.frecuencia().periodoA()).isEqualByComparingTo("2");
        assertThat(resultado.frecuencia().periodoB()).isEqualByComparingTo("5");
        assertThat(resultado.frecuencia().variacionAbsoluta()).isEqualByComparingTo("3");
        assertThat(resultado.frecuencia().variacionPorcentual()).isEqualByComparingTo("150.00");
        assertThat(resultado.valor().variacionAbsoluta()).isEqualByComparingTo("419.50");
        assertThat(resultado.valor().variacionPorcentual()).isEqualByComparingTo("87.30");
        assertThat(resultado.valor().tendencia()).isEqualTo(TendenciaConsumo.CRECE);
        assertThat(resultado.sinDatos()).isFalse();
        assertThat(resultado.mensaje()).isNull();
    }

    @Test
    void unPeriodoSinComprasApareceEnCeroYLaVariacionSeCalculaSinError() {
        List<Identificador> ids = List.of(new Identificador("CLI-5521"));
        when(agregado.resumir(ids, INICIO_A, FIN_A)).thenReturn(ResumenCompras.VACIO);
        when(agregado.resumir(ids, INICIO_B, FIN_B)).thenReturn(new ResumenCompras(1, new BigDecimal("100.00")));

        ResultadoComparacion resultado = consulta.compararCliente(ids, periodos);

        assertThat(resultado.frecuencia().periodoA()).isEqualByComparingTo("0");
        assertThat(resultado.valor().periodoA()).isEqualByComparingTo("0");
        assertThat(resultado.valor().variacionAbsoluta()).isEqualByComparingTo("100.00");
        assertThat(resultado.valor().variacionPorcentual()).isNull();
        assertThat(resultado.valor().tendencia()).isEqualTo(TendenciaConsumo.NUEVO);
        assertThat(resultado.sinDatos()).isFalse();
    }

    @Test
    void sinComprasEnNingunPeriodoSeAvisaPeroNoEsUnError() {
        List<Identificador> ids = List.of(new Identificador("CLI-5521"));
        when(agregado.resumir(any(), any(), any())).thenReturn(ResumenCompras.VACIO);

        ResultadoComparacion resultado = consulta.compararCliente(ids, periodos);

        assertThat(resultado.sinDatos()).isTrue();
        assertThat(resultado.mensaje()).contains("ninguno de los dos periodos");
        assertThat(resultado.valor().tendencia()).isEqualTo(TendenciaConsumo.ESTABLE);
    }

    @Test
    void unIdentificadorRepetidoNoDuplicaLasCompras() {
        when(agregado.resumir(any(), any(), any())).thenReturn(ResumenCompras.VACIO);

        consulta.compararCliente(List.of(new Identificador("CLI-1"), new Identificador("CLI-1")), periodos);

        verify(agregado).resumir(List.of(new Identificador("CLI-1")), INICIO_A, FIN_A);
        verify(agregado).resumir(List.of(new Identificador("CLI-1")), INICIO_B, FIN_B);
    }

    @Test
    void alCompararUnSegmentoSoloAgregaALosClientesDeEseSegmento() {
        when(clientesDelSegmento.idsDe("FRECUENTE")).thenReturn(List.of(7L, 42L, 42L));
        when(agregado.resumirPerfiles(List.of(7L, 42L), INICIO_A, FIN_A))
                .thenReturn(new ResumenCompras(4, new BigDecimal("1000.00")));
        when(agregado.resumirPerfiles(List.of(7L, 42L), INICIO_B, FIN_B))
                .thenReturn(new ResumenCompras(3, new BigDecimal("750.00")));

        ResultadoComparacion resultado = consulta.compararSegmento(" FRECUENTE ", periodos);

        assertThat(resultado.alcance().tipo()).isEqualTo("SEGMENTO");
        assertThat(resultado.alcance().segmento()).isEqualTo("FRECUENTE");
        assertThat(resultado.alcance().clientes()).isEqualTo(2);
        assertThat(resultado.frecuencia().variacionPorcentual()).isEqualByComparingTo("-25.00");
        assertThat(resultado.valor().variacionPorcentual()).isEqualByComparingTo("-25.00");
        assertThat(resultado.valor().tendencia()).isEqualTo(TendenciaConsumo.DECAE);
        verifyNoInteractionsConCompraPorIdentificador();
    }

    @Test
    void unSegmentoSinClientesDevuelveCeroConUnMensajeSinConsultarCompras() {
        when(clientesDelSegmento.idsDe("NUEVO")).thenReturn(List.of());

        ResultadoComparacion resultado = consulta.compararSegmento("NUEVO", periodos);

        assertThat(resultado.alcance().clientes()).isZero();
        assertThat(resultado.sinDatos()).isTrue();
        assertThat(resultado.mensaje()).contains("'NUEVO'").contains("no tiene clientes");
        assertThat(resultado.valor().periodoA()).isEqualByComparingTo("0");
        verifyNoInteractions(agregado);
    }

    @Test
    void unSegmentoVacioOMuyLargoSeRechazaSinConsultar() {
        assertThatThrownBy(() -> consulta.compararSegmento("  ", periodos))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> consulta.compararSegmento(null, periodos))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> consulta.compararSegmento("S".repeat(61), periodos))
                .isInstanceOf(ReglaNegocioException.class);

        verifyNoInteractions(clientesDelSegmento, agregado);
    }

    @Test
    void siSegmentacionNoRespondeLaComparacionFallaEnVezDeMostrarCeros() {
        when(clientesDelSegmento.idsDe("FRECUENTE"))
                .thenThrow(new ServicioNoDisponibleException("segmentación caída", new RuntimeException()));

        assertThatThrownBy(() -> consulta.compararSegmento("FRECUENTE", periodos))
                .isInstanceOf(ServicioNoDisponibleException.class);
    }

    private void verifyNoInteractionsConCompraPorIdentificador() {
        verify(agregado, org.mockito.Mockito.never()).resumir(any(), any(), any());
    }
}
