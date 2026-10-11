package com.maxiconecta.crm.comportamiento.periodo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.maxiconecta.crm.comportamiento.comun.ManejadorDeErrores;
import com.maxiconecta.crm.comportamiento.compra.Identificador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-349, SCRUM-350 · Formato de la respuesta de la consulta por periodo y rechazo de las
 * entradas inválidas (SCRUM-39).
 */
class ValorPorPeriodoControllerTest {

    private static final String RUTA = "/api/comportamiento/clientes/42/consumo-periodo";

    private ConsultaValorPorPeriodo consulta;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        consulta = mock(ConsultaValorPorPeriodo.class);
        // Igual que Spring Boot: las fechas salen como texto ISO (2026-09-01), no como un arreglo numérico.
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mvc = MockMvcBuilders.standaloneSetup(new ValorPorPeriodoController(consulta))
                .setControllerAdvice(new ManejadorDeErrores())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .build();
    }

    @Test
    void devuelveElTotalElPromedioYElPeriodoConsultado() throws Exception {
        LocalDate desde = LocalDate.parse("2026-09-01");
        LocalDate hasta = LocalDate.parse("2026-09-30");
        RangoPeriodo rango = new RangoPeriodo(desde, hasta);
        when(consulta.consultar(List.of(new Identificador("CLI-5521"), new Identificador("mp-user-3307")), desde,
                hasta)).thenReturn(ValorPorPeriodo.de(rango, "America/La_Paz",
                new com.maxiconecta.crm.comportamiento.compra.ResumenCompras(2, new BigDecimal("480.50"))));

        mvc.perform(get(RUTA).param("identificador", "CLI-5521", "mp-user-3307")
                        .param("desde", "2026-09-01").param("hasta", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value("2026-09-01"))
                .andExpect(jsonPath("$.hasta").value("2026-09-30"))
                .andExpect(jsonPath("$.zonaHoraria").value("America/La_Paz"))
                .andExpect(jsonPath("$.total").value(480.50))
                .andExpect(jsonPath("$.promedio").value(240.25))
                .andExpect(jsonPath("$.compras").value(2))
                .andExpect(jsonPath("$.sinDatos").value(false))
                .andExpect(jsonPath("$.mensaje").doesNotExist());
    }

    @Test
    void unPeriodoSinComprasResponde200ConCeroYUnMensaje() throws Exception {
        LocalDate desde = LocalDate.parse("2026-01-01");
        LocalDate hasta = LocalDate.parse("2026-01-31");
        when(consulta.consultar(List.of(), desde, hasta)).thenReturn(ValorPorPeriodo.de(new RangoPeriodo(desde, hasta),
                "America/La_Paz", com.maxiconecta.crm.comportamiento.compra.ResumenCompras.VACIO));

        mvc.perform(get(RUTA).param("desde", "2026-01-01").param("hasta", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.promedio").value(0))
                .andExpect(jsonPath("$.compras").value(0))
                .andExpect(jsonPath("$.sinDatos").value(true))
                .andExpect(jsonPath("$.mensaje").value("El cliente no tiene compras vigentes entre 2026-01-01 y 2026-01-31."));

        verify(consulta).consultar(List.of(), desde, hasta);
    }

    @Test
    void unRangoAlRevesOIncompletoSeRechazaConUn400() throws Exception {
        when(consulta.consultar(List.of(), LocalDate.parse("2026-09-30"), LocalDate.parse("2026-09-01")))
                .thenThrow(new com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException("rango al revés"));
        when(consulta.consultar(List.of(), null, LocalDate.parse("2026-09-30")))
                .thenThrow(new com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException("falta desde"));

        mvc.perform(get(RUTA).param("desde", "2026-09-30").param("hasta", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("rango al revés"));
        mvc.perform(get(RUTA).param("hasta", "2026-09-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("falta desde"));
    }

    @Test
    void unaFechaConFormatoInvalidoSeRechazaConUn400SinConsultar() throws Exception {
        mvc.perform(get(RUTA).param("desde", "01/09/2026").param("hasta", "2026-09-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Valor no válido para 'desde': 01/09/2026"));
        mvc.perform(get(RUTA).param("desde", "2026-02-30").param("hasta", "2026-09-30"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(consulta);
    }

    @Test
    void unIdentificadorMalFormadoSeRechazaConUn400SinConsultar() throws Exception {
        mvc.perform(get(RUTA).param("identificador", " ").param("desde", "2026-09-01").param("hasta", "2026-09-30"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(consulta);
    }
}
