package com.maxiconecta.crm.perfil.consulta;

import com.maxiconecta.crm.perfil.cliente.EstadoPerfil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-279 · Resúmenes de varios perfiles por identificador, que servicio-segmentacion usa para
 * nombrar a los clientes del listado por segmento, sin cambiar la búsqueda de perfiles existente.
 */
class PerfilControllerTest {

    private ConsultaPerfil consulta;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        consulta = mock(ConsultaPerfil.class);
        mvc = MockMvcBuilders.standaloneSetup(new PerfilController(consulta)).build();
    }

    @Test
    void conIdDevuelveLosResumenesDeEsosPerfiles() throws Exception {
        when(consulta.resumenes(List.of(42L, 43L))).thenReturn(List.of(
                new ResumenPerfil(42L, "Ana", "Pérez", "CI", "4455667", EstadoPerfil.COMPLETO, null, null)));

        mvc.perform(get("/api/perfil/clientes").param("id", "42", "43"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(42))
                .andExpect(jsonPath("$[0].nombres").value("Ana"))
                .andExpect(jsonPath("$[0].apellidos").value("Pérez"));
    }

    @Test
    void sinIdSigueSiendoLaBusquedaPaginada() throws Exception {
        when(consulta.buscar(any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/perfil/clientes").param("numeroDocumento", "4455667"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientes").isArray());

        verify(consulta, never()).resumenes(any());
    }
}
