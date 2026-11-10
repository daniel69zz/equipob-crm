package com.maxiconecta.crm.segmentacion.reporte;

import com.maxiconecta.crm.segmentacion.reporte.DistribucionRepository.Conteo;
import com.maxiconecta.crm.segmentacion.reporte.NivelesFidelizacion.ConteoNiveles;
import com.maxiconecta.crm.segmentacion.reporte.ReporteDistribucion.Grupo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCRUM-57 · Cantidades y porcentajes del reporte de distribución de clientes: los tres cortes sobre
 * la misma población (criterio 1), el reporte sin datos (criterio 2) y el corte de fidelización
 * cuando servicio-fidelizacion no responde.
 */
class ServicioReporteDistribucionTest {

    private static final Instant AHORA = Instant.parse("2026-11-09T15:30:00Z");

    private DistribucionRepository distribucion;
    private NivelesFidelizacion fidelizacion;
    private AuditoriaReportes auditoria;
    private ServicioReporteDistribucion servicio;

    @BeforeEach
    void preparar() {
        distribucion = mock(DistribucionRepository.class);
        fidelizacion = mock(NivelesFidelizacion.class);
        auditoria = mock(AuditoriaReportes.class);
        servicio = new ServicioReporteDistribucion(distribucion, fidelizacion, auditoria,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    // --- Criterio 1: cantidad y porcentaje por segmento, nivel de consumo y nivel de fidelización ---

    @Test
    void devuelveLaCantidadYElPorcentajeDeLosTresCortes() {
        FiltroReporte filtro = FiltroReporte.de(null, null);
        when(distribucion.total(filtro)).thenReturn(8L);
        when(distribucion.porSegmento(filtro)).thenReturn(List.of(
                new Conteo("Alto valor", 5), new Conteo("Frecuente", 2), new Conteo(null, 1)));
        when(distribucion.porNivelConsumo(filtro)).thenReturn(List.of(
                new Conteo("BAJO", 1), new Conteo("MEDIO", 3), new Conteo("ALTO", 4)));
        when(fidelizacion.todos()).thenReturn(new ConteoNiveles(Map.of("BRONCE", 4L, "PLATA", 2L, "ORO", 1L), true));

        ReporteDistribucion reporte = servicio.generar(filtro, "luis");

        assertThat(reporte.generadoEn()).isEqualTo(OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC));
        assertThat(reporte.total()).isEqualTo(8);
        assertThat(reporte.sinDatos()).isFalse();
        assertThat(reporte.fidelizacionDisponible()).isTrue();
        assertThat(reporte.segmentos()).containsExactly(
                grupo("Alto valor", 5, "62.50"), grupo("Frecuente", 2, "25.00"), grupo(null, 1, "12.50"));
        assertThat(reporte.nivelesConsumo()).containsExactly(
                grupo("BAJO", 1, "12.50"), grupo("MEDIO", 3, "37.50"), grupo("ALTO", 4, "50.00"));
        // El cliente sin puntos o sin nivel asignado entra en el grupo sin nivel: 8 - (4+2+1) = 1.
        assertThat(reporte.nivelesFidelizacion()).containsExactly(
                grupo("BRONCE", 4, "50.00"), grupo("PLATA", 2, "25.00"), grupo("ORO", 1, "12.50"),
                grupo(null, 1, "12.50"));
    }

    @Test
    void lasCantidadesDeCadaCorteSumanElTotal() {
        FiltroReporte filtro = FiltroReporte.de(null, null);
        when(distribucion.total(filtro)).thenReturn(3L);
        when(distribucion.porSegmento(filtro)).thenReturn(List.of(new Conteo("Alto valor", 2), new Conteo(null, 1)));
        when(distribucion.porNivelConsumo(filtro)).thenReturn(List.of(new Conteo("ALTO", 3)));
        when(fidelizacion.todos()).thenReturn(new ConteoNiveles(Map.of("ORO", 3L), true));

        ReporteDistribucion reporte = servicio.generar(filtro, "luis");

        assertThat(suma(reporte.segmentos())).isEqualTo(3);
        assertThat(suma(reporte.nivelesConsumo())).isEqualTo(3);
        assertThat(suma(reporte.nivelesFidelizacion())).isEqualTo(3);
    }

    @Test
    void elPorcentajeDeUnTercioSeRedondeaADosDecimales() {
        FiltroReporte filtro = FiltroReporte.de(null, null);
        when(distribucion.total(filtro)).thenReturn(3L);
        when(distribucion.porSegmento(filtro)).thenReturn(List.of(new Conteo("Alto valor", 1)));
        when(distribucion.porNivelConsumo(filtro)).thenReturn(List.of());
        when(fidelizacion.todos()).thenReturn(new ConteoNiveles(Map.of(), true));

        ReporteDistribucion reporte = servicio.generar(filtro, "luis");

        assertThat(reporte.segmentos()).containsExactly(grupo("Alto valor", 1, "33.33"));
    }

    // --- Criterio 2: sin datos para un filtro, se indica sin error ---

    @Test
    void unFiltroSinClientesDevuelveSinDatosYNoConsultaLosCortes() {
        FiltroReporte filtro = FiltroReporte.de("Sin clientes", null);
        when(distribucion.total(filtro)).thenReturn(0L);

        ReporteDistribucion reporte = servicio.generar(filtro, "luis");

        assertThat(reporte.sinDatos()).isTrue();
        assertThat(reporte.total()).isZero();
        assertThat(reporte.segmentos()).isEmpty();
        assertThat(reporte.nivelesConsumo()).isEmpty();
        assertThat(reporte.nivelesFidelizacion()).isEmpty();
        assertThat(reporte.filtro().segmento()).isEqualTo("Sin clientes");
        verify(distribucion, never()).porSegmento(any());
        verify(distribucion, never()).porNivelConsumo(any());
        verify(fidelizacion, never()).todos();
        verify(auditoria).registrar(eq("luis"), eq(filtro), eq(0L), eq(true));
    }

    // --- El corte de fidelización cuando el servicio no responde ---

    @Test
    void siFidelizacionNoRespondeElReporteSeEntregaConLosOtrosDosCortes() {
        FiltroReporte filtro = FiltroReporte.de(null, null);
        when(distribucion.total(filtro)).thenReturn(2L);
        when(distribucion.porSegmento(filtro)).thenReturn(List.of(new Conteo("Alto valor", 2)));
        when(distribucion.porNivelConsumo(filtro)).thenReturn(List.of(new Conteo("ALTO", 2)));
        when(fidelizacion.todos()).thenReturn(new ConteoNiveles(Map.of(), false));

        ReporteDistribucion reporte = servicio.generar(filtro, "luis");

        assertThat(reporte.fidelizacionDisponible()).isFalse();
        assertThat(reporte.nivelesFidelizacion()).isEmpty();
        assertThat(reporte.segmentos()).hasSize(1);
        assertThat(reporte.nivelesConsumo()).hasSize(1);
        verify(auditoria).registrar(eq("luis"), eq(filtro), eq(2L), eq(false));
    }

    // --- Con filtro, los niveles se piden solo para los clientes del reporte ---

    @Test
    void conFiltroLosNivelesSePidenParaLosClientesDelReporteYNoParaTodos() {
        FiltroReporte filtro = FiltroReporte.de("Alto valor", null);
        when(distribucion.total(filtro)).thenReturn(2L);
        when(distribucion.porSegmento(filtro)).thenReturn(List.of(new Conteo("Alto valor", 2)));
        when(distribucion.porNivelConsumo(filtro)).thenReturn(List.of(new Conteo("ALTO", 2)));
        when(distribucion.clientes(filtro)).thenReturn(List.of(501L, 502L));
        when(fidelizacion.deClientes(List.of(501L, 502L)))
                .thenReturn(new ConteoNiveles(Map.of("ORO", 2L), true));

        ReporteDistribucion reporte = servicio.generar(filtro, "luis");

        assertThat(reporte.nivelesFidelizacion()).containsExactly(
                grupo("BRONCE", 0, "0.00"), grupo("PLATA", 0, "0.00"), grupo("ORO", 2, "100.00"),
                grupo(null, 0, "0.00"));
        verify(fidelizacion).deClientes(List.of(501L, 502L));
        verify(fidelizacion, never()).todos();
    }

    @Test
    void siElRegistroDeAuditoriaFallaElReporteNoSeEntrega() {
        FiltroReporte filtro = FiltroReporte.de(null, null);
        when(distribucion.total(filtro)).thenReturn(1L);
        when(distribucion.porSegmento(filtro)).thenReturn(List.of(new Conteo("Alto valor", 1)));
        when(distribucion.porNivelConsumo(filtro)).thenReturn(List.of(new Conteo("ALTO", 1)));
        when(fidelizacion.todos()).thenReturn(new ConteoNiveles(Map.of("ORO", 1L), true));
        org.mockito.Mockito.doThrow(new IllegalStateException("auditoría caída"))
                .when(auditoria).registrar(anyString(), any(), anyLong(), anyBoolean());

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> servicio.generar(filtro, "luis")))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Grupo grupo(String valor, long clientes, String porcentaje) {
        return new Grupo(valor, clientes, new java.math.BigDecimal(porcentaje));
    }

    private static long suma(List<Grupo> grupos) {
        return grupos.stream().mapToLong(Grupo::clientes).sum();
    }
}
