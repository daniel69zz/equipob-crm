package com.maxiconecta.crm.segmentacion.consulta;

import com.maxiconecta.crm.segmentacion.segmento.SegmentoClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-278 · El servicio vuelve a comprobar, con la identidad que reenvía el Gateway, que el rol
 * tenga SEGMENTOS_CONSULTAR antes de listar clientes por segmento.
 */
@WebMvcTest(controllers = {ClientesSegmentoController.class, SegmentacionController.class})
@Import(PermisoConsultaClientes.class)
class PermisoConsultaClientesTest {

    private static final String PERMISOS_GERENTE =
            "CLIENTE_CONSULTAR,INDICADORES_CONSULTAR,SEGMENTOS_CONSULTAR,PUNTOS_CONSULTAR,INTERACCIONES_CONSULTAR";
    private static final String PERMISOS_AGENTE =
            "CLIENTE_CONSULTAR,INDICADORES_CONSULTAR,PUNTOS_CONSULTAR,INTERACCIONES_CONSULTAR,INTERACCIONES_REGISTRAR";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ConsultaClientesSegmento consulta;

    @MockBean
    private SegmentoClienteRepository segmentos;

    @BeforeEach
    void respuestasVacias() {
        when(consulta.buscar(any(), any(), any(), anyInt(), anyInt(), anyString()))
                .thenReturn(new ListadoClientes(List.of(), 0, 20, 0, "valor", "desc", true));
        when(consulta.segmentos()).thenReturn(List.of());
        when(segmentos.findById(42L)).thenReturn(Optional.empty());
    }

    @Test
    void elGerenteComercialPuedeListarClientesPorSegmento() throws Exception {
        mvc.perform(como("luis", PERMISOS_GERENTE, get("/api/segmentacion/clientes").param("segmento", "Alto valor")))
                .andExpect(status().isOk());

        verify(consulta).buscar(any(), any(), any(), eq(0), eq(20), eq("luis"));
    }

    @Test
    void unRolSinElPermisoRecibe403YNoSeConsulta() throws Exception {
        mvc.perform(como("ana", PERMISOS_AGENTE, get("/api/segmentacion/clientes").param("segmento", "Alto valor")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Su rol no tiene permiso para consultar clientes por segmento"));

        verify(consulta, never()).buscar(any(), any(), any(), anyInt(), anyInt(), anyString());
    }

    @Test
    void sinLaIdentidadDelGatewayRecibe401() throws Exception {
        mvc.perform(get("/api/segmentacion/clientes").param("segmento", "Alto valor")
                        .header("X-Usuario-Permisos", PERMISOS_GERENTE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Se requiere la identidad del Gateway"));
    }

    @Test
    void elCatalogoDeSegmentosTambienExigeElPermiso() throws Exception {
        mvc.perform(como("ana", PERMISOS_AGENTE, get("/api/segmentacion/segmentos"))).andExpect(status().isForbidden());
        mvc.perform(como("luis", PERMISOS_GERENTE, get("/api/segmentacion/segmentos"))).andExpect(status().isOk());
    }

    @Test
    void elSegmentoDeUnClienteParaLaFichaIntegralNoCambia() throws Exception {
        // servicio-perfil lo consulta directamente, sin pasar por el Gateway ni traer identidad.
        mvc.perform(get("/api/segmentacion/clientes/42/segmento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinDatos").value(true));
    }

    @Test
    void unFiltroInvalidoResponde400ConElMotivo() throws Exception {
        mvc.perform(como("luis", PERMISOS_GERENTE, get("/api/segmentacion/clientes")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Elija un segmento o un nivel de consumo"));
        mvc.perform(como("luis", PERMISOS_GERENTE, get("/api/segmentacion/clientes")
                        .param("segmento", "Alto valor").param("ultimaCompraDesde", "01/09/2026")))
                .andExpect(status().isBadRequest());
    }

    private static MockHttpServletRequestBuilder como(String usuario, String permisos,
                                                      MockHttpServletRequestBuilder solicitud) {
        return solicitud.header("X-Usuario", usuario).header("X-Usuario-Permisos", permisos);
    }
}
