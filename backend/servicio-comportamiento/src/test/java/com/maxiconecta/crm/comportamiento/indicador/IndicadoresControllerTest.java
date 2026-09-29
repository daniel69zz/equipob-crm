package com.maxiconecta.crm.comportamiento.indicador;

import com.maxiconecta.crm.comportamiento.comun.ManejadorDeErrores;
import com.maxiconecta.crm.comportamiento.compra.Identificador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-205 · Forma de la respuesta de indicadores y lectura de los identificadores del cliente.
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
        when(consulta.ticketPromedio(List.of(new Identificador("CLI-5521"),
                new Identificador("mp-user-3307"))))
                .thenReturn(new TicketPromedio(new BigDecimal("240.25"), 2, new BigDecimal("480.50"), false));
        when(consulta.recencia(List.of(new Identificador("CLI-5521"),
                new Identificador("mp-user-3307"))))
                .thenReturn(new RecenciaCompra(OffsetDateTime.parse("2026-09-27T15:28:10-04:00"),
                        Duration.ofHours(49).plusMinutes(30), false));

        mvc.perform(get("/api/comportamiento/clientes/42/indicadores")
                        .param("identificador", "CLI-5521", "mp-user-3307"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketPromedio.valor").value(240.25))
                .andExpect(jsonPath("$.ticketPromedio.compras").value(2))
                .andExpect(jsonPath("$.ticketPromedio.montoAcumulado").value(480.50))
                .andExpect(jsonPath("$.ticketPromedio.sinDatos").value(false))
                .andExpect(jsonPath("$.recencia.ultimaCompra").value("2026-09-27T15:28:10-04:00"))
                .andExpect(jsonPath("$.recencia.tiempoTranscurrido").value("PT49H30M"))
                .andExpect(jsonPath("$.recencia.sinDatos").value(false));
    }

    @Test
    void sinIdentificadoresResponde200ConElTicketSinDatos() throws Exception {
        when(consulta.ticketPromedio(List.of()))
                .thenReturn(new TicketPromedio(null, 0, BigDecimal.ZERO, true));
        when(consulta.recencia(List.of())).thenReturn(new RecenciaCompra(null, null, true));

        mvc.perform(get("/api/comportamiento/clientes/42/indicadores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketPromedio.valor").doesNotExist())
                .andExpect(jsonPath("$.ticketPromedio.sinDatos").value(true))
                .andExpect(jsonPath("$.ticketPromedio.compras").value(0))
                .andExpect(jsonPath("$.recencia.ultimaCompra").doesNotExist())
                .andExpect(jsonPath("$.recencia.tiempoTranscurrido").doesNotExist())
                .andExpect(jsonPath("$.recencia.sinDatos").value(true));

        verify(consulta).ticketPromedio(List.of());
        verify(consulta).recencia(List.of());
    }

    @Test
    void unIdentificadorMalFormadoSeRechazaConUn400SinConsultar() throws Exception {
        mvc.perform(get("/api/comportamiento/clientes/42/indicadores").param("identificador", " "))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/comportamiento/clientes/42/indicadores").param("identificador", "x".repeat(65)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(consulta);
    }
}
