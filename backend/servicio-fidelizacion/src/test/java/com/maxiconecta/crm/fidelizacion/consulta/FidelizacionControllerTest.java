package com.maxiconecta.crm.fidelizacion.consulta;

import com.maxiconecta.crm.fidelizacion.puntos.SaldoPuntos;
import com.maxiconecta.crm.fidelizacion.puntos.SaldoPuntosRepository;
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
 * SCRUM-154 · Consulta del saldo y nivel de puntos de un cliente.
 */
class FidelizacionControllerTest {

    private SaldoPuntosRepository saldos;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        saldos = mock(SaldoPuntosRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new FidelizacionController(saldos)).build();
    }

    @Test
    void devuelveElSaldoYNivelVigentes() throws Exception {
        when(saldos.findById(42L)).thenReturn(Optional.of(
                new SaldoPuntos(42L, 350, "PLATA", OffsetDateTime.parse("2026-09-01T10:00:00Z"))));

        mvc.perform(get("/api/fidelizacion/clientes/42/puntos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(350))
                .andExpect(jsonPath("$.nivel").value("PLATA"))
                .andExpect(jsonPath("$.sinDatos").value(false));
    }

    @Test
    void unClienteSinPuntosAsignadosRespondeSinDatosEnVezDeError() throws Exception {
        when(saldos.findById(7L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/fidelizacion/clientes/7/puntos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinDatos").value(true))
                .andExpect(jsonPath("$.saldo").value(org.hamcrest.Matchers.nullValue()));
    }
}
