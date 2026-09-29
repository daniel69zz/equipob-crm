package com.maxiconecta.crm.perfil.historial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxiconecta.crm.perfil.cliente.CambioCampo;
import com.maxiconecta.crm.perfil.cliente.CambioPerfil;
import com.maxiconecta.crm.perfil.cliente.CambioPerfilRepository;
import com.maxiconecta.crm.perfil.cliente.HistorialCambios;
import com.maxiconecta.crm.perfil.cliente.Origen;
import com.maxiconecta.crm.perfil.cliente.TipoCambio;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCRUM-236 · Reglas del guardado del histórico, sin base de datos.
 */
class HistorialCambiosTest {

    private final CambioPerfilRepository repository = mock(CambioPerfilRepository.class);
    private final HistorialCambios historial = new HistorialCambios(repository, new ObjectMapper());

    @Test
    void unaOperacionGuardaUnRegistroConUnaFilaPorCampo() {
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        CambioPerfil registro = historial.registrar(1L, TipoCambio.ACTUALIZACION, Origen.MARKETPLACE_VENTAS, "cajero.mlopez",
                List.of(new CambioCampo("email", "a@correo.com", "b@correo.com"), new CambioCampo("telefono", null, "+59170012345")),
                7L);

        assertThat(registro.getDetalles()).extracting(d -> d.getOrden() + " " + d.getCampo() + " " + d.getValorAnterior()
                        + "→" + d.getValorNuevo())
                .containsExactly("1 email a@correo.com→b@correo.com", "2 telefono null→+59170012345");
        assertThat(registro.getCambios()).contains("\"campo\":\"email\"");
    }

    @Test
    void sinCambiosNoSeRegistraNada() {
        assertThat(historial.registrar(1L, TipoCambio.ACTUALIZACION, Origen.MARKETPLACE_VENTAS, "x", List.of(), 7L)).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    void elOrigenTieneQueCorresponderAlTipoDeCambio() {
        List<CambioCampo> cambio = List.of(new CambioCampo("email", null, "a@correo.com"));

        assertThatThrownBy(() -> historial.registrar(1L, TipoCambio.VINCULACION, Origen.MARKETPLACE_VENTAS, "admin", cambio, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> historial.registrar(1L, TipoCambio.ACTUALIZACION, Origen.CRM, "admin", cambio, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> historial.registrar(1L, TipoCambio.ACTUALIZACION, Origen.MARKETPLACE_VENTAS, " ", cambio, null))
                .hasMessageContaining("responsable");
    }
}
