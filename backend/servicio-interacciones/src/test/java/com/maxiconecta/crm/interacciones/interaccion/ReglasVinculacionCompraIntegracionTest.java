package com.maxiconecta.crm.interacciones.interaccion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-447 · Permisos y consistencia de datos al vincular una compra a una interacción (SCRUM-54,
 * docs/interacciones/vinculacion-compras.md). Ningún rechazo cambia la interacción.
 */
class ReglasVinculacionCompraIntegracionTest extends IntegracionInteracciones {

    @BeforeEach
    void compras() {
        comprasDelCliente();
    }

    @Test
    void elGerenteYUnAgenteSinAccesoALasComprasNoVinculan() throws Exception {
        long id = registrarReclamo(AGENTE);

        conIdentidad(put(RUTA_INTERACCION + "/compra", CLIENTE, id), "gerente", "GERENTE_COMERCIAL", PERMISOS_AGENTE,
                cuerpo("referencia", "V-1001", "version", 1))
                .andExpect(status().isForbidden());
        conIdentidad(put(RUTA_INTERACCION + "/compra", CLIENTE, id), AGENTE, "AGENTE_ATENCION",
                "INTERACCIONES_CONSULTAR,INTERACCIONES_REGISTRAR", cuerpo("referencia", "V-1001", "version", 1))
                .andExpect(status().isForbidden());
        conIdentidad(delete(RUTA_INTERACCION + "/compra", CLIENTE, id).param("version", "1"), "gerente",
                "GERENTE_COMERCIAL", PERMISOS_AGENTE)
                .andExpect(status().isForbidden());

        sinCambios();
    }

    @Test
    void unaInteraccionCerradaNoSeVincula() throws Exception {
        long id = registrarReclamo(AGENTE);
        seguimiento(id, AGENTE, "AGENTE_ATENCION", cuerpo("estado", "CERRADA", "version", 1));

        vincular(id, AGENTE, "AGENTE_ATENCION", cuerpo("referencia", "V-1001", "version", 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La interacción está cerrada: reábrala para modificarla"));
        // Se rechaza sin consultar Comportamiento.
        verify(compras, never()).buscar(anyLong(), anyList(), anyString());
    }

    @Test
    void unaVersionAnteriorOLaMismaCompraSeRechazan() throws Exception {
        long id = registrarReclamo(AGENTE);

        vincular(id, AGENTE, "AGENTE_ATENCION", cuerpo("referencia", "V-1001", "version", 5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campos.version").exists());
        vincular(id, AGENTE, "AGENTE_ATENCION", cuerpo("referencia", "V-1001", "version", 1))
                .andExpect(status().isOk());
        vincular(id, AGENTE, "AGENTE_ATENCION", cuerpo("referencia", "V-1001", "version", 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.referencia").value("ya es la compra vinculada"));
        conIdentidad(delete(RUTA_INTERACCION + "/compra", CLIENTE, id).param("version", "1"), AGENTE,
                "AGENTE_ATENCION", PERMISOS_AGENTE)
                .andExpect(status().isConflict());
    }

    @Test
    void elCuerpoSeValida() throws Exception {
        long id = registrarReclamo(AGENTE);

        vincular(id, AGENTE, "AGENTE_ATENCION", cuerpo("referencia", " ", "version", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.referencia").exists());
        vincular(id, AGENTE, "AGENTE_ATENCION", cuerpo("referencia", "V-1001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.version").exists());
        conIdentidad(put(RUTA_INTERACCION + "/compra", 43, id), AGENTE, "AGENTE_ATENCION", PERMISOS_AGENTE,
                cuerpo("referencia", "V-1001", "version", 1))
                .andExpect(status().isNotFound());

        sinCambios();
    }

    @Test
    void sinComportamientoOSinIdentificadoresDeVentasNoSeVincula() throws Exception {
        long id = registrarReclamo(AGENTE);
        when(compras.buscar(eq(CLIENTE), anyList(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "sin Comportamiento"));

        vincular(id, AGENTE, "AGENTE_ATENCION", cuerpo("referencia", "V-1001", "version", 1))
                .andExpect(status().isServiceUnavailable());

        when(perfiles.identificadores(CLIENTE)).thenReturn(Optional.of(List.of()));
        vincular(id, AGENTE, "AGENTE_ATENCION", cuerpo("referencia", "V-1001", "version", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.referencia").value("no es una compra del cliente"));

        sinCambios();
    }

    @Test
    void sinCompraVinculadaNoHayQueDesvincular() throws Exception {
        long id = registrarReclamo(AGENTE);

        conIdentidad(delete(RUTA_INTERACCION + "/compra", CLIENTE, id).param("version", "1"), AGENTE,
                "AGENTE_ATENCION", PERMISOS_AGENTE)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La interacción no tiene una compra vinculada"));
    }

    private void sinCambios() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM interacciones.interaccion "
                + "WHERE compra_referencia IS NOT NULL OR version > 1", Integer.class)).isZero();
    }
}
