package com.maxiconecta.crm.comportamiento.evolucion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.maxiconecta.crm.comportamiento.comun.ManejadorDeErrores;
import com.maxiconecta.crm.comportamiento.compra.Identificador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-307 · Lectura de los filtros y forma de la respuesta de la evolución del consumo.
 */
class EvolucionConsumoControllerTest {

    private static final String RUTA = "/api/comportamiento/clientes/42/evolucion-consumo";

    private ConsultaEvolucionConsumo consulta;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        consulta = mock(ConsultaEvolucionConsumo.class);
        when(consulta.hoy()).thenReturn(LocalDate.of(2026, 10, 8));
        when(consulta.consultar(anyList(), any())).thenAnswer(invocacion -> ejemplo(invocacion.getArgument(1)));
        // Fechas como texto ISO (2026-07-01), igual que el ObjectMapper de Spring Boot en la aplicación
        ObjectMapper json = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        mvc = MockMvcBuilders.standaloneSetup(new EvolucionConsumoController(consulta))
                .setControllerAdvice(new ManejadorDeErrores())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .build();
    }

    @Test
    void pasaLosIdentificadoresYLosFiltrosALaConsulta() throws Exception {
        mvc.perform(get(RUTA)
                        .param("identificador", "CLI-5521", "mp-user-3307")
                        .param("desde", "2026-07-01")
                        .param("hasta", "2026-09-30")
                        .param("periodo", "TRIMESTRAL")
                        .param("categoria", "Hogar", "Electrónica"))
                .andExpect(status().isOk());

        ArgumentCaptor<FiltroEvolucionConsumo> filtro = ArgumentCaptor.forClass(FiltroEvolucionConsumo.class);
        verify(consulta).consultar(eq(List.of(new Identificador("CLI-5521"), new Identificador("mp-user-3307"))),
                filtro.capture());
        assertThat(filtro.getValue().desde()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(filtro.getValue().hasta()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(filtro.getValue().periodo()).isEqualTo(TipoPeriodo.TRIMESTRAL);
        assertThat(filtro.getValue().categorias()).containsExactlyInAnyOrder("Hogar", "Electrónica");
    }

    @Test
    void unaCategoriaConComaNoSeParteEnDos() throws Exception {
        mvc.perform(get(RUTA).param("categoria", "Hogar, jardín")).andExpect(status().isOk());

        ArgumentCaptor<FiltroEvolucionConsumo> filtro = ArgumentCaptor.forClass(FiltroEvolucionConsumo.class);
        verify(consulta).consultar(anyList(), filtro.capture());
        assertThat(filtro.getValue().categorias()).containsExactly("Hogar, jardín");
    }

    @Test
    void sinFiltrosConsultaLosUltimosDoceMesesHastaHoy() throws Exception {
        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value("2025-11-01"))
                .andExpect(jsonPath("$.hasta").value("2026-10-08"))
                .andExpect(jsonPath("$.periodo").value("MENSUAL"))
                .andExpect(jsonPath("$.periodos.length()").value(12));
    }

    @Test
    void devuelveLosPeriodosLasCategoriasConSuDetalleComparativoYLaCalidadDeLosDatos() throws Exception {
        mvc.perform(get(RUTA).param("identificador", "CLI-5521")
                        .param("desde", "2026-08-01").param("hasta", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zonaHoraria").value("America/La_Paz"))
                .andExpect(jsonPath("$.periodoBase").value("2026-08"))
                .andExpect(jsonPath("$.periodos[0].clave").value("2026-08"))
                .andExpect(jsonPath("$.periodos[0].inicio").value("2026-08-01"))
                .andExpect(jsonPath("$.periodos[0].fin").value("2026-08-31"))
                .andExpect(jsonPath("$.periodos[0].parcial").value(false))
                .andExpect(jsonPath("$.categoriasDisponibles[0]").value("Hogar"))
                .andExpect(jsonPath("$.categorias[0].categoria").value("Hogar"))
                .andExpect(jsonPath("$.categorias[0].tendencia").value("CRECE"))
                .andExpect(jsonPath("$.categorias[0].evolucion[0].monto").value(0))
                .andExpect(jsonPath("$.categorias[0].evolucion[0].variacionAnterior").doesNotExist())
                .andExpect(jsonPath("$.categorias[0].evolucion[1].monto").value(89.90))
                .andExpect(jsonPath("$.categorias[0].evolucion[1].compras").value(1))
                .andExpect(jsonPath("$.categorias[0].evolucion[1].variacionAnterior.monto").value(89.90))
                .andExpect(jsonPath("$.categorias[0].evolucion[1].variacionAnterior.porcentaje").doesNotExist())
                .andExpect(jsonPath("$.totales[1].compras").value(1))
                .andExpect(jsonPath("$.calidadDatos.consistente").value(true))
                .andExpect(jsonPath("$.sinDatos").value(false));
    }

    @Test
    void parametrosInvalidosSeRechazanConUn400SinConsultar() throws Exception {
        mvc.perform(get(RUTA).param("desde", "2026-09-01").param("hasta", "2026-08-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La fecha 'desde' no puede ser posterior a 'hasta'"));
        mvc.perform(get(RUTA).param("desde", "01/09/2026")).andExpect(status().isBadRequest());
        mvc.perform(get(RUTA).param("periodo", "SEMANAL")).andExpect(status().isBadRequest());
        mvc.perform(get(RUTA).param("categoria", " ")).andExpect(status().isBadRequest());
        mvc.perform(get(RUTA).param("periodoBase", "1999-01")).andExpect(status().isBadRequest());
        mvc.perform(get(RUTA).param("identificador", " ")).andExpect(status().isBadRequest());

        verify(consulta, never()).consultar(anyList(), any());
    }

    /** Hogar: sin consumo en el primer periodo del filtro y 89.90 en el segundo. */
    private static EvolucionConsumo ejemplo(FiltroEvolucionConsumo filtro) {
        List<Periodo> periodos = filtro.periodos();
        List<ConsumoPeriodo> consumo = new ArrayList<>();
        for (int i = 0; i < periodos.size(); i++) {
            consumo.add(i == 1
                    ? new ConsumoPeriodo(periodos.get(i).clave(), new BigDecimal("89.90"), 1, 1)
                    : ConsumoPeriodo.sinConsumo(periodos.get(i).clave()));
        }
        SerieCategoria hogar = new SerieCategoria("Hogar", new BigDecimal("89.90"), 1, 1, consumo);
        List<TotalPeriodo> totales = consumo.stream()
                .map(c -> new TotalPeriodo(c.periodo(), c.monto(), c.compras())).toList();
        return new EvolucionConsumo(filtro.desde(), filtro.hasta(), filtro.periodo(), "America/La_Paz",
                filtro.periodoBase(), periodos, List.of("Hogar"),
                List.of(ComparacionPeriodos.comparar(hogar, filtro.indiceBase(), periodos)), totales,
                new CalidadDatos(1, 0, 0, 0, 0, 0, true), false);
    }
}
