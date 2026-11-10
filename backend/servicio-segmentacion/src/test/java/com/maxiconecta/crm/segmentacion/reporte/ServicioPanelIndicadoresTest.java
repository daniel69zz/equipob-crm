package com.maxiconecta.crm.segmentacion.reporte;

import com.maxiconecta.crm.segmentacion.reporte.DistribucionRepository.Conteo;
import com.maxiconecta.crm.segmentacion.reporte.IndicadoresExternos.ClientesActivos;
import com.maxiconecta.crm.segmentacion.reporte.IndicadoresExternos.PuntosAcumulados;
import com.maxiconecta.crm.segmentacion.reporte.ReporteDistribucion.Grupo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCRUM-471 · Consolidación del panel de indicadores (SCRUM-59): los tres indicadores juntos (criterio 1),
 * un servicio caído que no tumba el panel, y la cartera vacía.
 */
class ServicioPanelIndicadoresTest {

    private DistribucionRepository distribucion;
    private IndicadoresExternos externos;
    private AuditoriaReportes auditoria;
    private ServicioPanelIndicadores servicio;

    @BeforeEach
    void preparar() {
        distribucion = mock(DistribucionRepository.class);
        externos = mock(IndicadoresExternos.class);
        auditoria = mock(AuditoriaReportes.class);
        servicio = new ServicioPanelIndicadores(distribucion, externos, auditoria,
                Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void reuneClientesActivosDistribucionPorSegmentoYPuntosAcumulados() {
        when(distribucion.total(any())).thenReturn(4L);
        when(distribucion.porSegmento(any())).thenReturn(List.of(new Conteo("Alto valor", 3), new Conteo(null, 1)));
        when(externos.clientesActivos()).thenReturn(Optional.of(new ClientesActivos(3, 90)));
        when(externos.puntosAcumulados()).thenReturn(Optional.of(new PuntosAcumulados(110, 2)));

        PanelIndicadores panel = servicio.consultar("luis");

        assertThat(panel.clientes()).isEqualTo(4);
        assertThat(panel.sinDatos()).isFalse();
        assertThat(panel.activos().disponible()).isTrue();
        assertThat(panel.activos().activos()).isEqualTo(3);
        assertThat(panel.activos().porcentaje()).isEqualByComparingTo("75.00");
        assertThat(panel.activos().umbralDias()).isEqualTo(90);
        assertThat(panel.segmentos()).containsExactly(new Grupo("Alto valor", 3, new BigDecimal("75.00")),
                new Grupo(null, 1, new BigDecimal("25.00")));
        assertThat(panel.puntos().puntos()).isEqualTo(110);
        assertThat(panel.puntos().clientesConPuntos()).isEqualTo(2);
        verify(auditoria).registrarPanel("luis", 4, true, true);
    }

    @Test
    void siUnServicioNoRespondeSuIndicadorQuedaNoDisponibleYElPanelSeMuestraIgual() {
        when(distribucion.total(any())).thenReturn(2L);
        when(distribucion.porSegmento(any())).thenReturn(List.of(new Conteo("Alto valor", 2)));
        when(externos.clientesActivos()).thenReturn(Optional.empty());
        when(externos.puntosAcumulados()).thenReturn(Optional.of(new PuntosAcumulados(50, 1)));

        PanelIndicadores panel = servicio.consultar("luis");

        assertThat(panel.activos().disponible()).isFalse();
        assertThat(panel.activos().activos()).isNull();
        assertThat(panel.segmentos()).hasSize(1);
        assertThat(panel.puntos().disponible()).isTrue();
        verify(auditoria).registrarPanel("luis", 2, false, true);
    }

    @Test
    void sinClientesElPanelDiceSinDatosYNoAgrupaSegmentos() {
        when(distribucion.total(any())).thenReturn(0L);
        when(externos.clientesActivos()).thenReturn(Optional.of(new ClientesActivos(0, 90)));
        when(externos.puntosAcumulados()).thenReturn(Optional.of(new PuntosAcumulados(0, 0)));

        PanelIndicadores panel = servicio.consultar("luis");

        assertThat(panel.sinDatos()).isTrue();
        assertThat(panel.segmentos()).isEmpty();
        assertThat(panel.activos().porcentaje()).isEqualByComparingTo("0");
        verify(distribucion, never()).porSegmento(any());
    }
}
