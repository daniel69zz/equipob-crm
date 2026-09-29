package com.maxiconecta.crm.comportamiento.indicador;

import com.maxiconecta.crm.comportamiento.comun.ManejadorDeErrores;
import com.maxiconecta.crm.comportamiento.compra.Identificador;
import com.maxiconecta.crm.comportamiento.compra.Origen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-205 · Forma de la respuesta de indicadores y lectura de los identificadores por canal.
 */
class IndicadoresControllerTest {

    private ConsultaIndicadores consulta;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        consulta = mock(ConsultaIndicadores.class);
        mvc = MockMvcBuilders.standaloneSetup(new IndicadoresController(consulta))
                .setControllerAdvice(new ManejadorDeErrores())
                .build();
    }

    @Test
    void devuelveElTicketPromedioYLosDatosConLosQueSeCalculo() throws Exception {
        when(consulta.ticketPromedio(List.of(new Identificador(Origen.VENTAS, "CLI-5521"),
                new Identificador(Origen.MARKETPLACE, "mp-user-3307"))))
                .thenReturn(new TicketPromedio(new BigDecimal("240.25"), 2, new BigDecimal("480.50"), false));

        mvc.perform(get("/api/comportamiento/clientes/42/indicadores")
                        .param("identificador", "VENTAS:CLI-5521", "MARKETPLACE:mp-user-3307"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketPromedio.valor").value(240.25))
                .andExpect(jsonPath("$.ticketPromedio.compras").value(2))
                .andExpect(jsonPath("$.ticketPromedio.montoAcumulado").value(480.50))
                .andExpect(jsonPath("$.ticketPromedio.sinDatos").value(false));
    }

    @Test
    void sinIdentificadoresResponde200ConElTicketSinDatos() throws Exception {
        when(consulta.ticketPromedio(List.of()))
                .thenReturn(new TicketPromedio(null, 0, BigDecimal.ZERO, true));

        mvc.perform(get("/api/comportamiento/clientes/42/indicadores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketPromedio.valor").doesNotExist())
                .andExpect(jsonPath("$.ticketPromedio.sinDatos").value(true))
                .andExpect(jsonPath("$.ticketPromedio.compras").value(0));

        verify(consulta).ticketPromedio(List.of());
    }

    @Test
    void unIdentificadorMalFormadoSeRechazaConUn400SinConsultar() throws Exception {
        mvc.perform(get("/api/comportamiento/clientes/42/indicadores").param("identificador", "CLI-5521"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/comportamiento/clientes/42/indicadores").param("identificador", "PUNTOS:CLI-5521"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(consulta);
    }
}
