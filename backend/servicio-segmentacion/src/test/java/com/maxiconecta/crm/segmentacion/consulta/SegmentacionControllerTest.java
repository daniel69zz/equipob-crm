package com.maxiconecta.crm.segmentacion.consulta;

import com.maxiconecta.crm.segmentacion.segmento.SegmentoCliente;
import com.maxiconecta.crm.segmentacion.segmento.SegmentoClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-149 · Consulta del segmento asignado a un cliente.
 */
class SegmentacionControllerTest {

    private SegmentoClienteRepository segmentos;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        segmentos = mock(SegmentoClienteRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new SegmentacionController(segmentos)).build();
    }

    @Test
    void devuelveElSegmentoAsignado() throws Exception {
        when(segmentos.findById(42L)).thenReturn(Optional.of(
                new SegmentoCliente(42L, "FRECUENTE", OffsetDateTime.parse("2026-09-01T10:00:00Z"))));

        mvc.perform(get("/api/segmentacion/clientes/42/segmento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segmento").value("FRECUENTE"))
                .andExpect(jsonPath("$.sinDatos").value(false));
    }

    @Test
    void unClienteSinSegmentoAsignadoRespondeSinDatosEnVezDeError() throws Exception {
        when(segmentos.findById(7L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/segmentacion/clientes/7/segmento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinDatos").value(true))
                .andExpect(jsonPath("$.segmento").value(org.hamcrest.Matchers.nullValue()));
    }
}
