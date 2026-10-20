package com.maxiconecta.crm.segmentacion.consulta;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.maxiconecta.crm.segmentacion.comun.ManejadorDeErrores;
import com.maxiconecta.crm.segmentacion.segmento.SegmentoCliente;
import com.maxiconecta.crm.segmentacion.segmento.SegmentoClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-357 · Clientes de un segmento, que consulta servicio-comportamiento para comparar periodos
 * por segmento (SCRUM-40).
 */
class ClientesDeSegmentoControllerTest {

    private SegmentoClienteRepository segmentos;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        segmentos = mock(SegmentoClienteRepository.class);
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mvc = MockMvcBuilders.standaloneSetup(new ClientesDeSegmentoController(segmentos))
                .setControllerAdvice(new ManejadorDeErrores())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .build();
    }

    @Test
    void devuelveSoloLosClientesDelSegmentoPedido() throws Exception {
        OffsetDateTime asignado = OffsetDateTime.parse("2026-09-01T10:00:00Z");
        when(segmentos.findBySegmentoOrderByClienteId(eq("FRECUENTE"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(new SegmentoCliente(7L, "FRECUENTE", asignado),
                        new SegmentoCliente(42L, "FRECUENTE", asignado)), PageRequest.of(0, 100), 2));

        mvc.perform(get("/api/segmentacion/segmentos/miembros").param("segmento", "FRECUENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segmento").value("FRECUENTE"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.content[0].clienteId").value(7))
                .andExpect(jsonPath("$.content[1].clienteId").value(42))
                .andExpect(jsonPath("$.content[1].asignadoEn").value("2026-09-01T10:00:00Z"));
    }

    @Test
    void unSegmentoSinClientesResponde200ConUnaPaginaVacia() throws Exception {
        when(segmentos.findBySegmentoOrderByClienteId(eq("NUEVO"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        mvc.perform(get("/api/segmentacion/segmentos/miembros").param("segmento", "NUEVO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void elTamanioDePaginaTieneUnMaximoYLaPaginaNoEsNegativa() throws Exception {
        when(segmentos.findBySegmentoOrderByClienteId(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1000), 0));

        mvc.perform(get("/api/segmentacion/segmentos/miembros").param("segmento", "VIP").param("tamanio", "50000")
                .param("pagina", "-3")).andExpect(status().isOk());

        verify(segmentos).findBySegmentoOrderByClienteId("VIP", PageRequest.of(0, 1000));
    }

    @Test
    void unSegmentoVacioOMuyLargoSeRechazaConUn400() throws Exception {
        mvc.perform(get("/api/segmentacion/segmentos/miembros").param("segmento", "  "))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/segmentacion/segmentos/miembros").param("segmento", "S".repeat(61)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(segmentos);
    }
}
