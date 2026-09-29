package com.maxiconecta.crm.perfil.cliente;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCRUM-165 · Registrar y etiquetar el estado de validación del perfil.
 */
class ClienteEstadoTest {

    @Test
    void sinMotivosElPerfilQuedaCompleto() {
        Cliente cliente = new Cliente();

        List<CambioCampo> cambios = cliente.marcarEstado(List.of());

        assertThat(cliente.getEstado()).isEqualTo(EstadoPerfil.COMPLETO);
        assertThat(cliente.getMotivosIncidencia()).isNull();
        assertThat(cambios).extracting(CambioCampo::campo).containsExactly("estado");
    }

    @Test
    void conMotivosDeCompletitudElPerfilQuedaIncompleto() {
        Cliente cliente = new Cliente();

        List<CambioCampo> cambios = cliente.marcarEstado(List.of("numeroDocumento: vacío"));

        assertThat(cliente.getEstado()).isEqualTo(EstadoPerfil.INCOMPLETO);
        assertThat(cliente.getMotivosIncidencia()).isEqualTo("numeroDocumento: vacío");
        assertThat(cambios).extracting(CambioCampo::campo)
                .containsExactly("estado", "motivosIncompleto", "motivosIncidencia");
    }

    @Test
    void conMotivosDeCoherenciaElPerfilQuedaInconsistente() {
        Cliente cliente = new Cliente();

        List<CambioCampo> cambios = cliente.marcarInconsistente(
                List.of("direcciones: ninguna dirección principal"));

        assertThat(cliente.getEstado()).isEqualTo(EstadoPerfil.INCONSISTENTE);
        assertThat(cliente.getMotivosIncidencia()).isEqualTo("direcciones: ninguna dirección principal");
        assertThat(cambios).extracting(CambioCampo::campo)
                .containsExactly("estado", "motivosInconsistencia", "motivosIncidencia");
    }

    @Test
    void marcarInconsistenteSinMotivosFalla() {
        Cliente cliente = new Cliente();

        assertThatThrownBy(() -> cliente.marcarInconsistente(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
