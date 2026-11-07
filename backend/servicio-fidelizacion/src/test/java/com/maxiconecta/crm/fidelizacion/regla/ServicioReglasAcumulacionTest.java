package com.maxiconecta.crm.fidelizacion.regla;

import com.maxiconecta.crm.fidelizacion.comun.ConflictoException;
import com.maxiconecta.crm.fidelizacion.comun.ReglaInvalidaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SCRUM-392 · Guardar una regla crea una versión nueva sobre la vigente, sin pisar cambios ajenos. */
class ServicioReglasAcumulacionTest {

    private final ReglasAcumulacion reglas = mock(ReglasAcumulacion.class);
    private final ServicioReglasAcumulacion servicio = new ServicioReglasAcumulacion(reglas);

    @BeforeEach
    void preparar() {
        when(reglas.vigente()).thenReturn(new ReglaAcumulacion(3, new BigDecimal("10.00"), new BigDecimal("0.00"), null,
                "admin", OffsetDateTime.parse("2026-11-01T00:00:00Z")));
    }

    @Test
    void guardaLaSiguienteVersionConElUsuarioYElMotivo() {
        when(reglas.guardar(4, new BigDecimal("5"), new BigDecimal("20"), "Campaña", "admin")).thenReturn(
                new ReglaAcumulacion(4, new BigDecimal("5"), new BigDecimal("20"), "Campaña", "admin", OffsetDateTime.now()));

        ReglaAcumulacion nueva = servicio.actualizar(solicitud("5", "20", "  Campaña ", 3L), "admin");

        assertThat(nueva.version()).isEqualTo(4);
        verify(reglas).guardar(4, new BigDecimal("5"), new BigDecimal("20"), "Campaña", "admin");
    }

    @Test
    void unMotivoEnBlancoSeGuardaVacio() {
        when(reglas.guardar(eq(4L), any(), any(), isNull(), eq("admin"))).thenReturn(
                new ReglaAcumulacion(4, new BigDecimal("5"), BigDecimal.ZERO, null, "admin", OffsetDateTime.now()));

        servicio.actualizar(solicitud("5", "0", "   ", 3L), "admin");

        verify(reglas).guardar(eq(4L), any(), any(), isNull(), eq("admin"));
    }

    @Test
    void siLaReglaCambioMientrasSeEditabaSeRechazaSinGuardar() {
        assertThatThrownBy(() -> servicio.actualizar(solicitud("5", "0", null, 2L), "admin"))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("versión vigente es la 3");
        verify(reglas, never()).guardar(anyLong(), any(), any(), any(), anyString());
    }

    @Test
    void dosAdministradoresQueGuardanALaVezNoSePisan() {
        when(reglas.guardar(anyLong(), any(), any(), any(), anyString())).thenThrow(new DuplicateKeyException("pk"));

        assertThatThrownBy(() -> servicio.actualizar(solicitud("5", "0", null, 3L), "admin"))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("al mismo tiempo");
    }

    @Test
    void unaReglaSinCambiosNoCreaUnaVersionNueva() {
        assertThatThrownBy(() -> servicio.actualizar(solicitud("10", "0.00", "nada", 3L), "admin"))
                .isInstanceOf(ReglaInvalidaException.class).hasMessageContaining("no cambió");
        verify(reglas, never()).guardar(anyLong(), any(), any(), any(), anyString());
    }

    private static SolicitudReglaAcumulacion solicitud(String tasa, String minimo, String motivo, Long version) {
        return new SolicitudReglaAcumulacion(new BigDecimal(tasa), new BigDecimal(minimo), motivo, version);
    }
}
