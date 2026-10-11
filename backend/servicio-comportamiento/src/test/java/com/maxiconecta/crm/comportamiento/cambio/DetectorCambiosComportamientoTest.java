package com.maxiconecta.crm.comportamiento.cambio;

import com.maxiconecta.crm.comportamiento.compra.AgregadoComprasCliente;
import com.maxiconecta.crm.comportamiento.compra.ComprasPorPeriodoCliente;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCRUM-315, SCRUM-319, SCRUM-320 · Registro de detecciones y estados de los clientes, sin base de datos.
 */
class DetectorCambiosComportamientoTest {

    private static final Instant REFERENCIA = Instant.parse("2026-10-10T12:00:00Z");
    private static final OffsetDateTime AHORA = REFERENCIA.atOffset(ZoneOffset.UTC);

    private final AgregadoComprasCliente agregado = mock(AgregadoComprasCliente.class);
    private final DeteccionCambioComportamientoRepository detecciones = mock(DeteccionCambioComportamientoRepository.class);
    private final EstadoComportamientoClienteRepository estados = mock(EstadoComportamientoClienteRepository.class);
    private final CriterioCambioComportamiento criterio = new CriterioCambioComportamiento(90, 3, 3,
            new BigDecimal("0.5"), Clock.fixed(REFERENCIA, ZoneOffset.UTC));
    private final DetectorCambiosComportamiento detector = new DetectorCambiosComportamiento(agregado, criterio,
            detecciones, estados);

    @Test
    void registraCadaVariacionRelevanteConLosValoresComparadosYElPeriodo() {
        clientes(new ComprasPorPeriodoCliente("CLI-1", AHORA.minusDays(360), 12L, new BigDecimal("1200"), 1L,
                new BigDecimal("100")));
        when(estados.findById("CLI-1")).thenReturn(Optional.empty());

        DetectorCambiosComportamiento.ResultadoDeteccion resultado = detector.detectarTodos();

        assertThat(resultado.evaluados()).isEqualTo(1);
        assertThat(resultado.deteccionesNuevas()).isEqualTo(2);
        ArgumentCaptor<DeteccionCambioComportamiento> guardadas = ArgumentCaptor.forClass(DeteccionCambioComportamiento.class);
        verify(detecciones, times(2)).save(guardadas.capture());
        DeteccionCambioComportamiento frecuencia = guardadas.getAllValues().get(0);
        assertThat(frecuencia.getIdClienteOrigen()).isEqualTo("CLI-1");
        assertThat(frecuencia.getTipo()).isEqualTo(TipoVariacion.FRECUENCIA);
        assertThat(frecuencia.getDireccion()).isEqualTo(DireccionVariacion.DISMINUCION);
        assertThat(frecuencia.getValorActual()).isEqualByComparingTo("1");
        assertThat(frecuencia.getValorHabitual()).isEqualByComparingTo("4");
        assertThat(frecuencia.getUmbral()).isEqualByComparingTo("0.5");
        assertThat(frecuencia.getPeriodoDesde()).isEqualTo(AHORA.minusDays(90));
        assertThat(frecuencia.getPeriodoHasta()).isEqualTo(AHORA);
        assertThat(frecuencia.getDetectadoEn()).isEqualTo(AHORA);
        verify(estados).save(any(EstadoComportamientoCliente.class));
    }

    @Test
    void unaVariacionYaDetectadaEnElPeriodoNoSeDuplica() {
        clientes(new ComprasPorPeriodoCliente("CLI-1", AHORA.minusDays(360), 12L, new BigDecimal("1200"), 1L,
                new BigDecimal("100")));
        when(estados.findById("CLI-1")).thenReturn(Optional.empty());
        when(detecciones.existsByIdClienteOrigenAndTipoAndDireccionAndDetectadoEnAfter(eq("CLI-1"),
                eq(TipoVariacion.FRECUENCIA), eq(DireccionVariacion.DISMINUCION), eq(AHORA.minusDays(90))))
                .thenReturn(true);

        DetectorCambiosComportamiento.ResultadoDeteccion resultado = detector.detectarTodos();

        assertThat(resultado.deteccionesNuevas()).isEqualTo(1);
        ArgumentCaptor<DeteccionCambioComportamiento> guardada = ArgumentCaptor.forClass(DeteccionCambioComportamiento.class);
        verify(detecciones).save(guardada.capture());
        assertThat(guardada.getValue().getTipo()).isEqualTo(TipoVariacion.MONTO);
    }

    @Test
    void unClienteSinLineaBaseQuedaMarcadoYNoGeneraDetecciones() {
        clientes(new ComprasPorPeriodoCliente("CLI-NUEVO", AHORA.minusDays(30), 0L, BigDecimal.ZERO, 2L,
                new BigDecimal("300")));
        EstadoComportamientoCliente anterior = new EstadoComportamientoCliente("CLI-NUEVO",
                EstadoComportamiento.ESTABLE, 5, AHORA.minusDays(1));
        when(estados.findById("CLI-NUEVO")).thenReturn(Optional.of(anterior));

        DetectorCambiosComportamiento.ResultadoDeteccion resultado = detector.detectarTodos();

        assertThat(resultado.sinLineaBase()).isEqualTo(1);
        assertThat(resultado.deteccionesNuevas()).isZero();
        assertThat(anterior.getEstado()).isEqualTo(EstadoComportamiento.SIN_LINEA_BASE);
        assertThat(anterior.getComprasLineaBase()).isZero();
        assertThat(anterior.getEvaluadoEn()).isEqualTo(AHORA);
        verify(detecciones, never()).save(any());
    }

    @Test
    void quitaElEstadoDeQuienesYaNoTienenComprasVigentes() {
        clientes();
        EstadoComportamientoCliente sinCompras = new EstadoComportamientoCliente("CLI-ANULADO",
                EstadoComportamiento.ESTABLE, 4, AHORA.minusDays(1));
        when(estados.findAll()).thenReturn(List.of(sinCompras));

        DetectorCambiosComportamiento.ResultadoDeteccion resultado = detector.detectarTodos();

        assertThat(resultado.evaluados()).isZero();
        verify(estados).delete(sinCompras);
        verify(detecciones, never()).existsByIdClienteOrigenAndTipoAndDireccionAndDetectadoEnAfter(anyString(),
                any(), any(), any());
    }

    private void clientes(ComprasPorPeriodoCliente... compras) {
        when(agregado.comprasPorPeriodo(AHORA.minusDays(360), AHORA.minusDays(90), AHORA)).thenReturn(List.of(compras));
    }
}
