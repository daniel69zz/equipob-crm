package com.maxiconecta.crm.comportamiento.reporte;

import com.maxiconecta.crm.comportamiento.comparacion.ClientesDelSegmento;
import com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException;
import com.maxiconecta.crm.comportamiento.compra.AgregadoConsumoCategorias;
import com.maxiconecta.crm.comportamiento.compra.AgregadoConsumoCategorias.ConsumoEnRango;
import com.maxiconecta.crm.comportamiento.compra.ResumenCompras;
import com.maxiconecta.crm.comportamiento.periodo.AgregadoConsumoPeriodo;
import com.maxiconecta.crm.comportamiento.periodo.RangoPeriodo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCRUM-469 · Bordes del filtro de segmento del reporte de comportamiento de compra (SCRUM-58): uno en
 * blanco es la cartera completa y uno demasiado largo se rechaza sin consultar a segmentación.
 */
class ConsultaReporteComportamientoTest {

    private static final RangoPeriodo SEPTIEMBRE = new RangoPeriodo(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

    private AgregadoConsumoPeriodo periodo;
    private AgregadoConsumoCategorias categorias;
    private ClientesDelSegmento clientesDelSegmento;
    private ConsultaReporteComportamiento consulta;

    @BeforeEach
    void preparar() {
        periodo = mock(AgregadoConsumoPeriodo.class);
        categorias = mock(AgregadoConsumoCategorias.class);
        clientesDelSegmento = mock(ClientesDelSegmento.class);
        consulta = new ConsultaReporteComportamiento(periodo, categorias, clientesDelSegmento,
                Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC), ZoneId.of("America/La_Paz"));
    }

    @Test
    void unSegmentoEnBlancoEsLaCarteraCompletaYNoConsultaASegmentacion() {
        when(periodo.resumirVinculadas(any(), any())).thenReturn(new ResumenCompras(2, new BigDecimal("300.00")));
        when(periodo.clientesConComprasVinculadas(any(), any())).thenReturn(2L);
        when(categorias.consumirVinculadas(any(), any())).thenReturn(ConsumoEnRango.VACIO);

        ReporteComportamiento reporte = consulta.generar(SEPTIEMBRE, "   ");

        assertThat(reporte.alcance().segmento()).isNull();
        assertThat(reporte.frecuencia().compras()).isEqualTo(2);
        assertThat(reporte.valor().valorPorCliente()).isEqualByComparingTo("150.00");
        verify(clientesDelSegmento, never()).idsDe(anyString());
    }

    @Test
    void elPeriodoSeTomaEnLaZonaDelNegocioConElUltimoDiaIncluido() {
        when(periodo.resumirVinculadas(any(), any())).thenReturn(ResumenCompras.VACIO);
        when(categorias.consumirVinculadas(any(), any())).thenReturn(ConsumoEnRango.VACIO);

        consulta.generar(SEPTIEMBRE, null);

        verify(periodo).resumirVinculadas(SEPTIEMBRE.inicio(ZoneId.of("America/La_Paz")),
                SEPTIEMBRE.fin(ZoneId.of("America/La_Paz")));
    }

    @Test
    void unSegmentoDeMasDeSesentaCaracteresSeRechazaSinConsultarASegmentacion() {
        assertThatThrownBy(() -> consulta.generar(SEPTIEMBRE, "x".repeat(61)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El segmento no puede tener más de 60 caracteres");
        verify(clientesDelSegmento, never()).idsDe(anyString());
    }

    @Test
    void sinComprasLaFrecuenciaYElValorQuedanEnCeroYLosPromediosNulos() {
        when(periodo.resumirVinculadas(any(), any())).thenReturn(ResumenCompras.VACIO);
        when(categorias.consumirVinculadas(any(), any())).thenReturn(ConsumoEnRango.VACIO);

        ReporteComportamiento reporte = consulta.generar(SEPTIEMBRE, null);

        assertThat(reporte.sinDatos()).isTrue();
        assertThat(reporte.mensaje()).isEqualTo("No hay compras vigentes en el periodo elegido.");
        assertThat(reporte.frecuencia().comprasPorCliente()).isNull();
        assertThat(reporte.valor().total()).isEqualByComparingTo("0");
        assertThat(reporte.valor().ticketPromedio()).isNull();
        assertThat(reporte.categorias()).isEmpty();
    }
}
