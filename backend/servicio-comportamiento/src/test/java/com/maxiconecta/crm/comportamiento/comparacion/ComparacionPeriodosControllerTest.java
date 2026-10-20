package com.maxiconecta.crm.comportamiento.comparacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.maxiconecta.crm.comportamiento.comparacion.ResultadoComparacion.AlcanceComparacion;
import com.maxiconecta.crm.comportamiento.comun.ManejadorDeErrores;
import com.maxiconecta.crm.comportamiento.comun.ServicioNoDisponibleException;
import com.maxiconecta.crm.comportamiento.compra.Identificador;
import com.maxiconecta.crm.comportamiento.compra.ResumenCompras;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-354, SCRUM-358 · Forma de la respuesta de la comparación entre periodos y rechazo de las
 * entradas inválidas (SCRUM-40).
 */
class ComparacionPeriodosControllerTest {

    private static final String RUTA_CLIENTE = "/api/comportamiento/clientes/42/comparacion-periodos";
    private static final String RUTA_SEGMENTO = "/api/comportamiento/comparacion-periodos";

    private ConsultaComparacionPeriodos consulta;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        consulta = mock(ConsultaComparacionPeriodos.class);
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mvc = MockMvcBuilders.standaloneSetup(new ComparacionPeriodosController(consulta))
                .setControllerAdvice(new ManejadorDeErrores())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .build();
    }

    private static MockHttpServletRequestBuilder conPeriodos(MockHttpServletRequestBuilder solicitud) {
        return solicitud.param("desdeA", "2026-08-01").param("hastaA", "2026-08-31")
                .param("desdeB", "2026-09-01").param("hastaB", "2026-09-30");
    }

    private static ResultadoComparacion resultado(AlcanceComparacion alcance, ResumenCompras a, ResumenCompras b) {
        PeriodosAComparar periodos = PeriodosAComparar.de(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"),
                LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"));
        return ResultadoComparacion.de(alcance, "America/La_Paz", periodos, a, b, null);
    }

    @Test
    void devuelveLaFrecuenciaYElValorDeCadaPeriodoConSuVariacion() throws Exception {
        when(consulta.compararCliente(any(), any())).thenReturn(resultado(AlcanceComparacion.cliente(),
                new ResumenCompras(2, new BigDecimal("480.50")), new ResumenCompras(5, new BigDecimal("900.00"))));

        mvc.perform(conPeriodos(get(RUTA_CLIENTE).param("identificador", "CLI-5521", "mp-user-3307")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alcance.tipo").value("CLIENTE"))
                .andExpect(jsonPath("$.zonaHoraria").value("America/La_Paz"))
                .andExpect(jsonPath("$.periodoA.desde").value("2026-08-01"))
                .andExpect(jsonPath("$.periodoA.hasta").value("2026-08-31"))
                .andExpect(jsonPath("$.periodoB.desde").value("2026-09-01"))
                .andExpect(jsonPath("$.periodoB.hasta").value("2026-09-30"))
                .andExpect(jsonPath("$.frecuencia.periodoA").value(2))
                .andExpect(jsonPath("$.frecuencia.periodoB").value(5))
                .andExpect(jsonPath("$.frecuencia.variacionAbsoluta").value(3))
                .andExpect(jsonPath("$.frecuencia.variacionPorcentual").value(150.00))
                .andExpect(jsonPath("$.frecuencia.tendencia").value("CRECE"))
                .andExpect(jsonPath("$.valor.periodoA").value(480.50))
                .andExpect(jsonPath("$.valor.periodoB").value(900.00))
                .andExpect(jsonPath("$.valor.variacionAbsoluta").value(419.50))
                .andExpect(jsonPath("$.valor.variacionPorcentual").value(87.30))
                .andExpect(jsonPath("$.sinDatos").value(false));
    }

    @Test
    void laComparacionDeUnSegmentoIndicaElSegmentoYCuantosClientesTiene() throws Exception {
        when(consulta.compararSegmento(any(), any())).thenReturn(resultado(AlcanceComparacion.segmento("FRECUENTE", 12),
                new ResumenCompras(4, new BigDecimal("1000.00")), new ResumenCompras(3, new BigDecimal("750.00"))));

        mvc.perform(conPeriodos(get(RUTA_SEGMENTO).param("segmento", "FRECUENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alcance.tipo").value("SEGMENTO"))
                .andExpect(jsonPath("$.alcance.segmento").value("FRECUENTE"))
                .andExpect(jsonPath("$.alcance.clientes").value(12))
                .andExpect(jsonPath("$.valor.tendencia").value("DECAE"));
    }

    @Test
    void unPeriodoEnCeroDejaElPorcentajeNuloSinError() throws Exception {
        when(consulta.compararCliente(any(), any())).thenReturn(resultado(AlcanceComparacion.cliente(),
                ResumenCompras.VACIO, new ResumenCompras(1, new BigDecimal("100.00"))));

        mvc.perform(conPeriodos(get(RUTA_CLIENTE).param("identificador", "CLI-5521")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor.periodoA").value(0))
                .andExpect(jsonPath("$.valor.variacionAbsoluta").value(100.00))
                .andExpect(jsonPath("$.valor.variacionPorcentual").doesNotExist())
                .andExpect(jsonPath("$.valor.tendencia").value("NUEVO"));
    }

    @Test
    void faltarUnaFechaOSuperponerLosPeriodosSeRechazaConUn400SinConsultar() throws Exception {
        mvc.perform(get(RUTA_CLIENTE).param("desdeA", "2026-08-01").param("hastaA", "2026-08-31")
                        .param("desdeB", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("hastaB")));
        mvc.perform(get(RUTA_CLIENTE).param("desdeA", "2026-08-01").param("hastaA", "2026-09-15")
                        .param("desdeB", "2026-09-01").param("hastaB", "2026-09-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("superponerse")));
        mvc.perform(get(RUTA_SEGMENTO).param("segmento", "VIP").param("desdeA", "2026-08-31")
                        .param("hastaA", "2026-08-01").param("desdeB", "2026-09-01").param("hastaB", "2026-09-30"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(consulta);
    }

    @Test
    void unaFechaConFormatoInvalidoOUnIdentificadorMalFormadoSeRechazaConUn400() throws Exception {
        mvc.perform(get(RUTA_CLIENTE).param("desdeA", "01/08/2026").param("hastaA", "2026-08-31")
                        .param("desdeB", "2026-09-01").param("hastaB", "2026-09-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Valor no válido para 'desdeA': 01/08/2026"));
        mvc.perform(conPeriodos(get(RUTA_CLIENTE).param("identificador", " ")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(consulta);
    }

    @Test
    void siSegmentacionNoRespondeLaComparacionPorSegmentoDevuelveUn503() throws Exception {
        when(consulta.compararSegmento(any(), any()))
                .thenThrow(new ServicioNoDisponibleException("No se pudo consultar el segmento 'FRECUENTE'",
                        new RuntimeException()));

        mvc.perform(conPeriodos(get(RUTA_SEGMENTO).param("segmento", "FRECUENTE")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("FRECUENTE")));
    }

    @Test
    void losIdentificadoresSeLeenComoEnElResto() throws Exception {
        when(consulta.compararCliente(any(), any())).thenReturn(resultado(AlcanceComparacion.cliente(),
                ResumenCompras.VACIO, ResumenCompras.VACIO));

        mvc.perform(conPeriodos(get(RUTA_CLIENTE))).andExpect(status().isOk());

        org.mockito.Mockito.verify(consulta).compararCliente(org.mockito.ArgumentMatchers.eq(List.<Identificador>of()),
                any());
    }
}
