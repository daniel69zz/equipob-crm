package com.maxiconecta.crm.fidelizacion.regla;

import com.maxiconecta.crm.fidelizacion.comun.ConflictoException;
import com.maxiconecta.crm.fidelizacion.comun.ManejadorDeErrores;
import com.maxiconecta.crm.fidelizacion.comun.Validaciones;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-46 · API de la regla de acumulación: validación de los parámetros (CA2, SCRUM-395), historial
 * (CA3) y acceso solo del Administrador de CRM (CA4).
 */
class ReglasAcumulacionControllerTest {

    private static final String URL = "/api/fidelizacion/reglas/acumulacion";

    private ServicioReglasAcumulacion servicio;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        servicio = mock(ServicioReglasAcumulacion.class);
        LocalValidatorFactoryBean validador = new LocalValidatorFactoryBean();
        validador.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new ReglasAcumulacionController(servicio, new Validaciones(validador)))
                .setControllerAdvice(new ManejadorDeErrores()).build();
    }

    // --- CA1: el administrador guarda una regla válida ---

    @Test
    void elAdministradorGuardaUnaReglaValida() throws Exception {
        when(servicio.actualizar(any(), eq("admin"))).thenReturn(new ReglaAcumulacion(2, new BigDecimal("5.00"),
                new BigDecimal("20.00"), "Campaña", "admin", OffsetDateTime.parse("2026-11-07T10:00:00Z")));

        mvc.perform(administrador(put(URL)).content(cuerpo("5.00", "20.00", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.bolivianosPorPunto").value(5.00))
                .andExpect(jsonPath("$.montoMinimo").value(20.00))
                .andExpect(jsonPath("$.modificadoPor").value("admin"));
    }

    @Test
    void laReglaVigenteSeConsulta() throws Exception {
        when(servicio.vigente()).thenReturn(new ReglaAcumulacion(1, new BigDecimal("10.00"), BigDecimal.ZERO, null,
                "sistema", OffsetDateTime.parse("2026-11-01T00:00:00Z")));

        mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.bolivianosPorPunto").value(10.00));
    }

    // --- CA2: valores negativos o vacíos se rechazan indicando el campo ---

    @Test
    void unaTasaNegativaSeRechazaIndicandoElCampo() throws Exception {
        mvc.perform(administrador(put(URL)).content(cuerpo("-5", "0", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("bolivianosPorPunto debe ser mayor que cero")))
                .andExpect(jsonPath("$.campos.bolivianosPorPunto").value("debe ser mayor que cero"));
        verify(servicio, never()).actualizar(any(), anyString());
    }

    @Test
    void unMontoMinimoNegativoSeRechazaIndicandoElCampo() throws Exception {
        mvc.perform(administrador(put(URL)).content(cuerpo("10", "-0.01", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.montoMinimo").value("no puede ser negativo"));
    }

    @Test
    void losCamposVaciosSeRechazanIndicandoCadaUno() throws Exception {
        mvc.perform(administrador(put(URL)).content("{\"bolivianosPorPunto\": null, \"version\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.bolivianosPorPunto").value("es obligatorio"))
                .andExpect(jsonPath("$.campos.montoMinimo").value("es obligatorio"));
    }

    @Test
    void unaTasaCeroOConDemasiadosDecimalesSeRechaza() throws Exception {
        mvc.perform(administrador(put(URL)).content(cuerpo("0", "0", 1)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.bolivianosPorPunto").exists());
        mvc.perform(administrador(put(URL)).content(cuerpo("10.005", "0", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.bolivianosPorPunto").value(containsString("2 decimales")));
    }

    @Test
    void unValorQueNoEsNumeroSeRechaza() throws Exception {
        mvc.perform(administrador(put(URL)).content("{\"bolivianosPorPunto\": \"diez\", \"montoMinimo\": 0, \"version\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("JSON")));
    }

    @Test
    void unCambioConcurrenteRespondeConflicto() throws Exception {
        when(servicio.actualizar(any(), anyString())).thenThrow(new ConflictoException("La regla cambió"));

        mvc.perform(administrador(put(URL)).content(cuerpo("5", "0", 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La regla cambió"));
    }

    // --- CA3: historial con la versión anterior, quién y cuándo ---

    @Test
    void elHistorialMuestraLaVersionAnteriorQuienYCuando() throws Exception {
        ReglaAcumulacion v2 = new ReglaAcumulacion(2, new BigDecimal("5.00"), new BigDecimal("20.00"), "Campaña",
                "admin", OffsetDateTime.parse("2026-11-07T10:00:00Z"));
        ReglaAcumulacion v1 = new ReglaAcumulacion(1, new BigDecimal("10.00"), new BigDecimal("0.00"), null,
                "sistema", OffsetDateTime.parse("2026-11-01T00:00:00Z"));
        when(servicio.historial()).thenReturn(List.of(
                new ReglasAcumulacion.CambioRegla(v2, v1.bolivianosPorPunto(), v1.montoMinimo()),
                new ReglasAcumulacion.CambioRegla(v1, null, null)));

        mvc.perform(administrador(get(URL + "/historial")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[0].modificadoPor").value("admin"))
                .andExpect(jsonPath("$[0].vigenteDesde").exists())
                .andExpect(jsonPath("$[0].anteriorBolivianosPorPunto").value(10.00))
                .andExpect(jsonPath("$[0].anteriorMontoMinimo").value(0.00))
                .andExpect(jsonPath("$[1].anteriorBolivianosPorPunto").doesNotExist());
    }

    // --- CA4: sin rol de Administrador de CRM, el acceso se deniega ---

    @Test
    void unAgenteConPermisoPorErrorNoPuedeCambiarLaRegla() throws Exception {
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(cuerpo("5", "0", 1))
                        .header("X-Usuario", "agente").header("X-Usuario-Rol", "AGENTE_ATENCION")
                        .header("X-Usuario-Permisos", "PUNTOS_CONSULTAR,FIDELIZACION_CONFIGURAR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(containsString("Solo el Administrador de CRM")));
        verify(servicio, never()).actualizar(any(), anyString());
    }

    @Test
    void unAdministradorSinElPermisoNoPuedeCambiarLaRegla() throws Exception {
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(cuerpo("5", "0", 1))
                        .header("X-Usuario", "admin").header("X-Usuario-Rol", "ADMINISTRADOR_CRM")
                        .header("X-Usuario-Permisos", "PUNTOS_CONSULTAR"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinIdentidadDelGatewayNoSeAccede() throws Exception {
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(cuerpo("5", "0", 1)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(URL + "/historial")).andExpect(status().isUnauthorized());
    }

    @Test
    void elAccesoSeDeniegaAntesDeValidarElContenido() throws Exception {
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(cuerpo("-1", "-1", 1))
                        .header("X-Usuario", "gerente").header("X-Usuario-Rol", "GERENTE_COMERCIAL")
                        .header("X-Usuario-Permisos", "PUNTOS_CONSULTAR"))
                .andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder administrador(MockHttpServletRequestBuilder solicitud) {
        return solicitud.contentType(MediaType.APPLICATION_JSON).header("X-Usuario", "admin")
                .header("X-Usuario-Rol", "ADMINISTRADOR_CRM")
                .header("X-Usuario-Permisos", "PUNTOS_CONSULTAR,FIDELIZACION_CONFIGURAR");
    }

    private static String cuerpo(String tasa, String minimo, long version) {
        return "{\"bolivianosPorPunto\": %s, \"montoMinimo\": %s, \"motivo\": \"Campaña\", \"version\": %d}"
                .formatted(tasa, minimo, version);
    }
}
