package com.maxiconecta.crm.fidelizacion.reporte;

import com.maxiconecta.crm.fidelizacion.comun.ManejadorDeErrores;
import com.maxiconecta.crm.fidelizacion.puntos.SaldoPuntosRepository;
import com.maxiconecta.crm.fidelizacion.puntos.SaldoPuntosRepository.ConteoNivel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-464 · Conteo de clientes por nivel de fidelización que consume el reporte de distribución
 * (SCRUM-57): todos los clientes o solo un grupo, en orden de nivel y con el tope por petición.
 */
class DistribucionNivelesControllerTest {

    private static final String RUTA = "/api/fidelizacion/reportes/distribucion-niveles";

    private SaldoPuntosRepository saldos;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        saldos = mock(SaldoPuntosRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new DistribucionNivelesController(saldos))
                .setControllerAdvice(new ManejadorDeErrores()).build();
    }

    @Test
    void sinCuerpoCuentaATodosLosClientesConSaldo() throws Exception {
        when(saldos.contarPorNivel()).thenReturn(List.of(conteo("ORO", 3), conteo("BRONCE", 5), conteo("PLATA", 2)));

        mvc.perform(post(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10))
                // Del nivel más bajo al más alto.
                .andExpect(jsonPath("$.niveles[0].nivel").value("BRONCE"))
                .andExpect(jsonPath("$.niveles[0].clientes").value(5))
                .andExpect(jsonPath("$.niveles[1].nivel").value("PLATA"))
                .andExpect(jsonPath("$.niveles[2].nivel").value("ORO"))
                .andExpect(jsonPath("$.niveles[2].clientes").value(3));

        verify(saldos, never()).contarPorNivel(anyCollection());
    }

    @Test
    void elClienteConPuntosYSinNivelVaAlFinal() throws Exception {
        when(saldos.contarPorNivel()).thenReturn(List.of(conteo(null, 4), conteo("BRONCE", 1)));

        mvc.perform(post(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.niveles[0].nivel").value("BRONCE"))
                .andExpect(jsonPath("$.niveles[1].nivel").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.niveles[1].clientes").value(4));
    }

    @Test
    void conUnGrupoDeClientesCuentaSoloAEsos() throws Exception {
        when(saldos.contarPorNivel(Set.of(501L, 502L))).thenReturn(List.of(conteo("ORO", 2)));

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"clientes\":[501,502]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.niveles[0].nivel").value("ORO"));

        verify(saldos, never()).contarPorNivel();
    }

    @Test
    void unaListaVaciaCuentaATodos() throws Exception {
        when(saldos.contarPorNivel()).thenReturn(List.of(conteo("ORO", 1)));

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"clientes\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        verify(saldos).contarPorNivel();
    }

    @Test
    void losClientesRepetidosSeCuentanUnaVez() throws Exception {
        // El controlador quita los repetidos antes de contar: 501, 502, 501 son dos clientes.
        when(saldos.contarPorNivel(Set.of(501L, 502L))).thenReturn(List.of(conteo("ORO", 2)));

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientes\":[501,502,501]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void pasarseDelTopePorPeticionResponde400() throws Exception {
        String clientes = IntStream.rangeClosed(1, DistribucionNivelesController.MAXIMO_CLIENTES + 1)
                .mapToObj(Integer::toString).collect(java.util.stream.Collectors.joining(","));

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientes\":[" + clientes + "]}"))
                .andExpect(status().isBadRequest());
    }

    private static ConteoNivel conteo(String nivel, long clientes) {
        return new ConteoNivel() {

            @Override
            public String getNivel() {
                return nivel;
            }

            @Override
            public long getClientes() {
                return clientes;
            }
        };
    }
}
