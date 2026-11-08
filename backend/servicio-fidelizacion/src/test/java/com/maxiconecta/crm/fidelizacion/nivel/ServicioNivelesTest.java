package com.maxiconecta.crm.fidelizacion.nivel;

import com.maxiconecta.crm.fidelizacion.comun.ConflictoException;
import com.maxiconecta.crm.fidelizacion.comun.ReglaInvalidaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SCRUM-413 · Guardar los niveles crea una versión nueva y reclasifica a todos los clientes. */
class ServicioNivelesTest {

    private final ConfiguracionesNiveles configuraciones = mock(ConfiguracionesNiveles.class);
    private final AsignacionNiveles asignacion = mock(AsignacionNiveles.class);
    private final ServicioNiveles servicio = new ServicioNiveles(configuraciones, asignacion);

    @BeforeEach
    void preparar() {
        when(configuraciones.vigente()).thenReturn(new ConfiguracionNiveles(1, 12, ReglasNivelesTest.iniciales(), null,
                "sistema", OffsetDateTime.now()));
    }

    @Test
    void guardaLaSiguienteVersionYReclasificaATodos() {
        List<UmbralNivel> nuevos = List.of(new UmbralNivel(Nivel.BRONCE, 0L, 999L),
                new UmbralNivel(Nivel.PLATA, 1000L, 2999L), new UmbralNivel(Nivel.ORO, 3000L, null));
        when(asignacion.recalcularTodos()).thenReturn(7);

        ServicioNiveles.Resultado resultado = servicio.actualizar(new SolicitudNiveles(6, nuevos, " Ajuste ", 1L), "admin");

        verify(configuraciones).guardar(2, 6, nuevos, "Ajuste", "admin");
        verify(asignacion).recalcularTodos();
        assertThat(resultado.clientesReclasificados()).isEqualTo(7);
    }

    @Test
    void unosUmbralesQueSeSuperponenNoSeGuardan() {
        List<UmbralNivel> superpuestos = List.of(new UmbralNivel(Nivel.BRONCE, 0L, 600L),
                new UmbralNivel(Nivel.PLATA, 500L, 1999L), new UmbralNivel(Nivel.ORO, 2000L, null));

        assertThatThrownBy(() -> servicio.actualizar(new SolicitudNiveles(12, superpuestos, null, 1L), "admin"))
                .isInstanceOf(ReglaInvalidaException.class).hasMessageContaining("se superponen");
        verify(configuraciones, never()).guardar(anyLong(), anyInt(), anyList(), any(), anyString());
        verify(asignacion, never()).recalcularTodos();
    }

    @Test
    void siLosNivelesCambiaronMientrasSeEditabanSeRechaza() {
        assertThatThrownBy(() -> servicio.actualizar(new SolicitudNiveles(6, ReglasNivelesTest.iniciales(), null, 5L),
                "admin")).isInstanceOf(ConflictoException.class).hasMessageContaining("versión vigente es la 1");
    }

    @Test
    void unaConfiguracionSinCambiosNoCreaUnaVersionNueva() {
        assertThatThrownBy(() -> servicio.actualizar(new SolicitudNiveles(12, ReglasNivelesTest.iniciales(), null, 1L),
                "admin")).isInstanceOf(ReglaInvalidaException.class).hasMessageContaining("no cambiaron");
    }

    @Test
    void dosAdministradoresQueGuardanALaVezNoSePisan() {
        doThrow(new DuplicateKeyException("pk")).when(configuraciones)
                .guardar(eq(2L), anyInt(), anyList(), any(), anyString());

        assertThatThrownBy(() -> servicio.actualizar(new SolicitudNiveles(6, ReglasNivelesTest.iniciales(), null, 1L),
                "admin")).isInstanceOf(ConflictoException.class).hasMessageContaining("al mismo tiempo");
        verify(asignacion, never()).recalcularTodos();
    }
}
