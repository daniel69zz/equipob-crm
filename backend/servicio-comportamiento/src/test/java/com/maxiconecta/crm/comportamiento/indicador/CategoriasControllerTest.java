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
 * SCRUM-212 · Forma de la respuesta de las categorías más consumidas y validación de sus parámetros.
 */
class CategoriasControllerTest {

    private static final String RUTA = "/api/comportamiento/clientes/42/categorias";

    private ConsultaIndicadores consulta;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        consulta = mock(ConsultaIndicadores.class);
        mvc = MockMvcBuilders.standaloneSetup(new CategoriasController(consulta))
                .setControllerAdvice(new ManejadorDeErrores())
                .build();
    }

    @Test
    void devuelveLasCategoriasEnElOrdenDelRanking() throws Exception {
        when(consulta.categoriasMasConsumidas(List.of(new Identificador(Origen.VENTAS, "CLI-5521")), null))
                .thenReturn(List.of(
                        new CategoriaConsumida("Electrónica", 1, 1, new BigDecimal("300.00"), false),
                        new CategoriaConsumida("Accesorios", 1, 2, new BigDecimal("50.50"), false)));

        mvc.perform(get(RUTA).param("identificador", "VENTAS:CLI-5521"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorias.length()").value(2))
                .andExpect(jsonPath("$.categorias[0].categoria").value("Electrónica"))
                .andExpect(jsonPath("$.categorias[0].compras").value(1))
                .andExpect(jsonPath("$.categorias[0].unidades").value(1))
                .andExpect(jsonPath("$.categorias[0].monto").value(300.00))
                .andExpect(jsonPath("$.categorias[0].sinCategoria").value(false))
                .andExpect(jsonPath("$.categorias[1].categoria").value("Accesorios"));
    }

    @Test
    void sinCategoriasElResultadoEsUnaListaVaciaYNoUnError() throws Exception {
        when(consulta.categoriasMasConsumidas(List.of(), null)).thenReturn(List.of());

        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorias.length()").value(0));
    }

    @Test
    void elLimiteSePasaALaConsulta() throws Exception {
        when(consulta.categoriasMasConsumidas(List.of(new Identificador(Origen.VENTAS, "CLI-5521")), 3))
                .thenReturn(List.of());

        mvc.perform(get(RUTA).param("identificador", "VENTAS:CLI-5521").param("limite", "3"))
                .andExpect(status().isOk());

        verify(consulta).categoriasMasConsumidas(List.of(new Identificador(Origen.VENTAS, "CLI-5521")), 3);
    }

    @Test
    void rechazaUnLimiteInvalidoUnIdentificadorMalFormadoYUnLimiteQueNoEsNumero() throws Exception {
        mvc.perform(get(RUTA).param("limite", "0")).andExpect(status().isBadRequest());
        mvc.perform(get(RUTA).param("limite", "-2")).andExpect(status().isBadRequest());
        mvc.perform(get(RUTA).param("limite", "muchos")).andExpect(status().isBadRequest());
        mvc.perform(get(RUTA).param("identificador", "CLI-5521")).andExpect(status().isBadRequest());
        mvc.perform(get(RUTA).param("identificador", "PUNTOS:CLI-5521")).andExpect(status().isBadRequest());

        verifyNoInteractions(consulta);
    }
}
