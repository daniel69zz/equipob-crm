package com.maxiconecta.crm.segmentacion.reporte;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-462 · El servicio vuelve a comprobar, con la identidad que reenvía el Gateway, que el rol
 * tenga REPORTES_CONSULTAR antes de generar el reporte. Criterio de aceptación 3: el Agente de
 * Atención al Cliente recibe 403 aunque pueda consultar clientes y puntos.
 */
@WebMvcTest(controllers = ReportesController.class)
@Import(PermisoReportes.class)
class PermisoReportesTest {

    private static final String PERMISOS_GERENTE =
            "CLIENTE_CONSULTAR,INDICADORES_CONSULTAR,SEGMENTOS_CONSULTAR,PUNTOS_CONSULTAR,"
                    + "INTERACCIONES_CONSULTAR,REPORTES_CONSULTAR";
    private static final String PERMISOS_AGENTE =
            "CLIENTE_CONSULTAR,INDICADORES_CONSULTAR,PUNTOS_CONSULTAR,INTERACCIONES_CONSULTAR,"
                    + "INTERACCIONES_REGISTRAR";
    private static final String PERMISOS_SIN_REPORTES =
            "CLIENTE_CONSULTAR,INDICADORES_CONSULTAR,SEGMENTOS_CONSULTAR,PUNTOS_CONSULTAR";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ServicioReporteDistribucion servicio;

    @MockBean
    private ServicioPanelIndicadores panel;

    @BeforeEach
    void reporteVacio() {
        when(servicio.generar(any(), anyString())).thenReturn(new ReporteDistribucion(
                OffsetDateTime.parse("2026-11-09T15:30:00Z"), new ReporteDistribucion.Filtro(null, null), 0, true,
                List.of(), List.of(), List.of(), true));
    }

    @Test
    void elGerenteComercialPuedeGenerarElReporte() throws Exception {
        mvc.perform(como("luis", PERMISOS_GERENTE, get("/api/segmentacion/reportes/distribucion")))
                .andExpect(status().isOk());

        verify(servicio).generar(any(), eq("luis"));
    }

    @Test
    void elAgenteDeAtencionRecibe403YNoSeGeneraElReporte() throws Exception {
        mvc.perform(como("ana", PERMISOS_AGENTE, get("/api/segmentacion/reportes/distribucion")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje")
                        .value("Su rol no tiene permiso para consultar los reportes de clientes"));

        verify(servicio, never()).generar(any(), anyString());
    }

    @Test
    void consultarSegmentosNoAlcanzaParaElReporte() throws Exception {
        // REPORTES_CONSULTAR es propio del reporte: SEGMENTOS_CONSULTAR no lo sustituye.
        mvc.perform(como("luis", PERMISOS_SIN_REPORTES, get("/api/segmentacion/reportes/distribucion")))
                .andExpect(status().isForbidden());

        verify(servicio, never()).generar(any(), anyString());
    }

    @Test
    void sinLaIdentidadDelGatewayRecibe401() throws Exception {
        mvc.perform(get("/api/segmentacion/reportes/distribucion")
                        .header("X-Usuario-Permisos", PERMISOS_GERENTE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Se requiere la identidad del Gateway"));
    }

    @Test
    void unNivelDeConsumoInvalidoResponde400ConElMotivo() throws Exception {
        mvc.perform(como("luis", PERMISOS_GERENTE, get("/api/segmentacion/reportes/distribucion")
                        .param("nivelConsumo", "ALTISIMO")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje")
                        .value("Nivel de consumo inválido: 'ALTISIMO' (se espera BAJO, MEDIO o ALTO)"));
    }

    // --- SCRUM-59, criterio 3: el panel de indicadores tiene el mismo permiso que los reportes ---

    @Test
    void elGerenteComercialPuedeAbrirElPanelYElAgenteNo() throws Exception {
        when(panel.consultar(anyString())).thenReturn(new PanelIndicadores(OffsetDateTime.parse("2026-11-10T12:00:00Z"),
                0, true, PanelIndicadores.Activos.noDisponible(), List.of(), PanelIndicadores.Puntos.noDisponible()));

        mvc.perform(como("luis", PERMISOS_GERENTE, get("/api/segmentacion/reportes/panel")))
                .andExpect(status().isOk());
        verify(panel).consultar("luis");

        mvc.perform(como("ana", PERMISOS_AGENTE, get("/api/segmentacion/reportes/panel")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Su rol no tiene permiso para consultar los reportes de clientes"));
        mvc.perform(get("/api/segmentacion/reportes/panel")).andExpect(status().isUnauthorized());
        verify(panel, org.mockito.Mockito.times(1)).consultar(anyString());
    }

    private static MockHttpServletRequestBuilder como(String usuario, String permisos,
                                                      MockHttpServletRequestBuilder solicitud) {
        return solicitud.header("X-Usuario", usuario).header("X-Usuario-Permisos", permisos);
    }
}
