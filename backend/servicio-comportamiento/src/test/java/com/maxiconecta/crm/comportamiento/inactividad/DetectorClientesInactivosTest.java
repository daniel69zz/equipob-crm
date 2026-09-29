package com.maxiconecta.crm.comportamiento.inactividad;

import com.maxiconecta.crm.comportamiento.compra.AgregadoComprasCliente;
import com.maxiconecta.crm.comportamiento.compra.Identificador;
import com.maxiconecta.crm.comportamiento.compra.UltimaCompraCliente;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCRUM-294, SCRUM-296 · Detección y persistencia de los clientes inactivos, sin base de datos.
 */
class DetectorClientesInactivosTest {

    private static final OffsetDateTime CORTE = OffsetDateTime.parse("2026-07-01T00:00:00Z");

    private final AgregadoComprasCliente agregado = mock(AgregadoComprasCliente.class);
    private final ClienteInactivoRepository repositorio = mock(ClienteInactivoRepository.class);
    private final CriterioInactividad criterio = mock(CriterioInactividad.class);
    private final DetectorClientesInactivos detector = new DetectorClientesInactivos(agregado, repositorio, criterio);

    @Test
    void agregaComoInactivoAUnClienteNuevoQueCumpleElCriterio() {
        when(criterio.fechaCorte()).thenReturn(CORTE);
        OffsetDateTime ultimaCompra = OffsetDateTime.parse("2026-05-01T10:00:00Z");
        when(agregado.ultimaCompraVigentePorClienteAnteriorA(CORTE))
                .thenReturn(List.of(new UltimaCompraCliente("CLI-5521", ultimaCompra)));
        when(repositorio.findByIdClienteOrigen("CLI-5521")).thenReturn(Optional.empty());
        when(repositorio.findAll()).thenReturn(List.of());

        int total = detector.detectar();

        assertThat(total).isEqualTo(1);
        verify(repositorio).save(any(ClienteInactivo.class));
    }

    @Test
    void actualizaLaUltimaCompraDeUnClienteQueYaEstabaInactivo() {
        when(criterio.fechaCorte()).thenReturn(CORTE);
        OffsetDateTime ultimaCompra = OffsetDateTime.parse("2026-05-15T10:00:00Z");
        when(agregado.ultimaCompraVigentePorClienteAnteriorA(CORTE))
                .thenReturn(List.of(new UltimaCompraCliente("CLI-5521", ultimaCompra)));
        ClienteInactivo existente = new ClienteInactivo("CLI-5521", OffsetDateTime.parse("2026-04-01T10:00:00Z"));
        when(repositorio.findByIdClienteOrigen("CLI-5521")).thenReturn(Optional.of(existente));
        when(repositorio.findAll()).thenReturn(List.of(existente));

        detector.detectar();

        assertThat(existente.getUltimaCompra()).isEqualTo(ultimaCompra);
        verify(repositorio, never()).save(any(ClienteInactivo.class));
    }

    @Test
    void quitaAUnClienteQueYaNoCumpleElCriterioAunqueSigaPersistido() {
        when(criterio.fechaCorte()).thenReturn(CORTE);
        when(agregado.ultimaCompraVigentePorClienteAnteriorA(CORTE)).thenReturn(List.of());
        ClienteInactivo yaNoInactivo = new ClienteInactivo("mp-user-1", OffsetDateTime.parse("2026-04-01T10:00:00Z"));
        when(repositorio.findAll()).thenReturn(List.of(yaNoInactivo));

        int total = detector.detectar();

        assertThat(total).isZero();
        verify(repositorio).delete(yaNoInactivo);
    }

    @Test
    void reactivarQuitaAlClienteSinEsperarLaProximaEjecucion() {
        detector.reactivar("CLI-5521");

        verify(repositorio).deleteByIdClienteOrigen("CLI-5521");
    }

    // --- SCRUM-523: reevaluar a un cliente tras anularse una de sus compras ---

    @Test
    void reevaluarQuitaAlClienteQueSeQuedoSinComprasVigentes() {
        when(criterio.fechaCorte()).thenReturn(CORTE);
        when(agregado.ultimaCompraVigente(List.of(new Identificador("CLI-5521")))).thenReturn(Optional.empty());

        detector.reevaluar("CLI-5521");

        verify(repositorio).deleteByIdClienteOrigen("CLI-5521");
        verify(repositorio, never()).save(any(ClienteInactivo.class));
    }

    @Test
    void reevaluarAgregaComoInactivoAlClienteCuyaUltimaCompraVigenteQuedoFueraDelUmbral() {
        when(criterio.fechaCorte()).thenReturn(CORTE);
        OffsetDateTime anterior = OffsetDateTime.parse("2026-05-01T10:00:00Z");
        when(agregado.ultimaCompraVigente(List.of(new Identificador("CLI-5521")))).thenReturn(Optional.of(anterior));
        when(repositorio.findByIdClienteOrigen("CLI-5521")).thenReturn(Optional.empty());

        detector.reevaluar("CLI-5521");

        verify(repositorio).save(any(ClienteInactivo.class));
        verify(repositorio, never()).deleteByIdClienteOrigen("CLI-5521");
    }

    @Test
    void reevaluarActualizaLaUltimaCompraDeUnClienteQueYaEstabaInactivo() {
        when(criterio.fechaCorte()).thenReturn(CORTE);
        OffsetDateTime anterior = OffsetDateTime.parse("2026-05-01T10:00:00Z");
        when(agregado.ultimaCompraVigente(List.of(new Identificador("CLI-5521")))).thenReturn(Optional.of(anterior));
        ClienteInactivo existente = new ClienteInactivo("CLI-5521", OffsetDateTime.parse("2026-06-20T10:00:00Z"));
        when(repositorio.findByIdClienteOrigen("CLI-5521")).thenReturn(Optional.of(existente));

        detector.reevaluar("CLI-5521");

        assertThat(existente.getUltimaCompra()).isEqualTo(anterior);
        verify(repositorio, never()).save(any(ClienteInactivo.class));
    }

    @Test
    void reevaluarNoListaAlClienteCuyaUltimaCompraVigenteSigueDentroDelUmbral() {
        when(criterio.fechaCorte()).thenReturn(CORTE);
        OffsetDateTime reciente = OffsetDateTime.parse("2026-08-01T10:00:00Z");
        when(agregado.ultimaCompraVigente(List.of(new Identificador("CLI-5521")))).thenReturn(Optional.of(reciente));

        detector.reevaluar("CLI-5521");

        verify(repositorio).deleteByIdClienteOrigen("CLI-5521");
        verify(repositorio, never()).save(any(ClienteInactivo.class));
    }
}
