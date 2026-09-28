package com.maxiconecta.crm.gateway.auditoria;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-514 · Reglas del filtro de auditoría: cliente afectado y operación registrada.
 */
class AuditoriaAccesoFilterTest {

    @Test
    void tomaElClienteDeLaRutaEnTodosLosMicroservicios() {
        assertThat(AuditoriaAccesoFilter.clienteId("/api/perfil/clientes/42")).isEqualTo("42");
        assertThat(AuditoriaAccesoFilter.clienteId("/api/comportamiento/clientes/42/compras")).isEqualTo("42");
        assertThat(AuditoriaAccesoFilter.clienteId("/api/fidelizacion/clientes/C-7/puntos")).isEqualTo("C-7");
        assertThat(AuditoriaAccesoFilter.clienteId("/api/interacciones/clientes/42")).isEqualTo("42");
        assertThat(AuditoriaAccesoFilter.clienteId("/api/segmentacion/clientes/42/segmentos")).isEqualTo("42");
    }

    @Test
    void enListadosYBusquedasNoHayClienteAfectado() {
        assertThat(AuditoriaAccesoFilter.clienteId("/api/perfil/clientes")).isNull();
        assertThat(AuditoriaAccesoFilter.clienteId("/api/segmentacion/segmentos")).isNull();
    }

    @Test
    void recortaIdentificadoresDemasiadoLargos() {
        assertThat(AuditoriaAccesoFilter.clienteId("/api/perfil/clientes/" + "9".repeat(80))).hasSize(60);
    }

    @Test
    void laOperacionDependeDelMetodoYDeLaRespuesta() {
        assertThat(AuditoriaAccesoFilter.operacion("GET", 200)).isEqualTo(AuditoriaService.CLIENTE_CONSULTADO);
        assertThat(AuditoriaAccesoFilter.operacion("GET", 404)).isEqualTo(AuditoriaService.CLIENTE_CONSULTADO);
        assertThat(AuditoriaAccesoFilter.operacion("PUT", 200)).isEqualTo(AuditoriaService.CLIENTE_MODIFICADO);
        assertThat(AuditoriaAccesoFilter.operacion("DELETE", 204)).isEqualTo(AuditoriaService.CLIENTE_MODIFICADO);
        assertThat(AuditoriaAccesoFilter.operacion("GET", 401)).isEqualTo(AuditoriaService.ACCESO_DENEGADO);
        assertThat(AuditoriaAccesoFilter.operacion("POST", 403)).isEqualTo(AuditoriaService.ACCESO_DENEGADO);
    }
}
