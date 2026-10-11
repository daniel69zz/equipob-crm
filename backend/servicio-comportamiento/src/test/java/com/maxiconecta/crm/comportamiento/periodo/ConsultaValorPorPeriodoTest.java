package com.maxiconecta.crm.comportamiento.periodo;

import com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException;
import com.maxiconecta.crm.comportamiento.compra.Identificador;
import com.maxiconecta.crm.comportamiento.compra.ResumenCompras;
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
 * SCRUM-345, SCRUM-347, SCRUM-350 · Cálculo del valor total y promedio por compra de un periodo, sin
 * base de datos.
 */
class ConsultaValorPorPeriodoTest {

    private static final LocalDate DESDE = LocalDate.parse("2026-09-01");
    private static final LocalDate HASTA = LocalDate.parse("2026-09-30");
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-09-01T00:00:00-04:00");
    private static final OffsetDateTime FIN = OffsetDateTime.parse("2026-10-01T00:00:00-04:00");

    private final AgregadoConsumoPeriodo agregado = mock(AgregadoConsumoPeriodo.class);
    private final ConsultaValorPorPeriodo consulta = new ConsultaValorPorPeriodo(agregado,
            ZoneId.of("America/La_Paz"));

    @Test
    void calculaElTotalYElPromedioPorCompraDelPeriodo() {
        when(agregado.resumir(List.of(new Identificador("CLI-5521")), INICIO, FIN))
                .thenReturn(new ResumenCompras(2, new BigDecimal("480.50")));

        ValorPorPeriodo valor = consulta.consultar(List.of(new Identificador("CLI-5521")), DESDE, HASTA);

        assertThat(valor.total()).isEqualByComparingTo("480.50");
        assertThat(valor.promedio()).isEqualByComparingTo("240.25");
        assertThat(valor.compras()).isEqualTo(2);
        assertThat(valor.sinDatos()).isFalse();
        assertThat(valor.mensaje()).isNull();
        assertThat(valor.desde()).isEqualTo(DESDE);
        assertThat(valor.hasta()).isEqualTo(HASTA);
        assertThat(valor.zonaHoraria()).isEqualTo("America/La_Paz");
    }

    @Test
    void elPromedioSeRedondeaADosDecimales() {
        when(agregado.resumir(any(), any(), any())).thenReturn(new ResumenCompras(3, new BigDecimal("100.00")));

        ValorPorPeriodo valor = consulta.consultar(List.of(new Identificador("CLI-5521")), DESDE, HASTA);

        assertThat(valor.promedio()).isEqualByComparingTo("33.33");
        assertThat(valor.promedio().scale()).isEqualTo(2);
    }

    @Test
    void unPeriodoSinComprasDevuelveCeroConUnMensajeYNoUnError() {
        when(agregado.resumir(any(), any(), any())).thenReturn(ResumenCompras.VACIO);

        ValorPorPeriodo valor = consulta.consultar(List.of(new Identificador("CLI-5521")), DESDE, HASTA);

        assertThat(valor.total()).isEqualByComparingTo("0");
        assertThat(valor.promedio()).isEqualByComparingTo("0");
        assertThat(valor.compras()).isZero();
        assertThat(valor.sinDatos()).isTrue();
        assertThat(valor.mensaje()).contains("no tiene compras vigentes").contains("2026-09-01").contains("2026-09-30");
    }

    @Test
    void sinIdentificadoresTampocoEsUnErrorYSeMuestraEnCero() {
        when(agregado.resumir(List.of(), INICIO, FIN)).thenReturn(ResumenCompras.VACIO);

        ValorPorPeriodo valor = consulta.consultar(List.of(), DESDE, HASTA);

        assertThat(valor.sinDatos()).isTrue();
        assertThat(valor.total()).isEqualByComparingTo("0");
    }

    @Test
    void unIdentificadorRepetidoNoDuplicaLasCompras() {
        when(agregado.resumir(any(), any(), any())).thenReturn(ResumenCompras.VACIO);

        consulta.consultar(List.of(new Identificador("CLI-5521"), new Identificador("CLI-5521"),
                new Identificador("mp-user-3307")), DESDE, HASTA);

        verify(agregado).resumir(List.of(new Identificador("CLI-5521"), new Identificador("mp-user-3307")), INICIO,
                FIN);
    }

    @Test
    void unRangoInvalidoSeRechazaSinConsultarLaBase() {
        assertThatThrownBy(() -> consulta.consultar(List.of(new Identificador("CLI-5521")), HASTA, DESDE))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> consulta.consultar(List.of(new Identificador("CLI-5521")), null, HASTA))
                .isInstanceOf(ReglaNegocioException.class);

        verifyNoInteractions(agregado);
    }
}
