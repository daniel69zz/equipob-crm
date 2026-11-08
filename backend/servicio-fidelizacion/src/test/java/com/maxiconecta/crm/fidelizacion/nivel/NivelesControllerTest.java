package com.maxiconecta.crm.fidelizacion.nivel;

import com.maxiconecta.crm.fidelizacion.comun.ManejadorDeErrores;
import com.maxiconecta.crm.fidelizacion.comun.Validaciones;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SCRUM-413 · API de gestión de niveles: umbrales en conflicto (CA2) y acceso del Administrador de CRM. */
class NivelesControllerTest {

    private static final String URL = "/api/fidelizacion/niveles";

    private ServicioNiveles servicio;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        servicio = mock(ServicioNiveles.class);
        LocalValidatorFactoryBean validador = new LocalValidatorFactoryBean();
        validador.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new NivelesController(servicio, new Validaciones(validador)))
                .setControllerAdvice(new ManejadorDeErrores()).build();
    }

    @Test
    void laConfiguracionVigenteMuestraLosUmbralesDeCadaNivel() throws Exception {
        when(servicio.vigente()).thenReturn(new ConfiguracionNiveles(1, 12, ReglasNivelesTest.iniciales(), null,
                "sistema", OffsetDateTime.parse("2026-11-01T00:00:00Z")));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodoMeses").value(12))
                .andExpect(jsonPath("$.umbrales[0].nivel").value("BRONCE"))
                .andExpect(jsonPath("$.umbrales[1].puntosMinimos").value(500))
                .andExpect(jsonPath("$.umbrales[2].puntosMaximos").doesNotExist());
    }

    @Test
    void elAdministradorGuardaLosNivelesYSabeCuantosClientesCambiaron() throws Exception {
        when(servicio.actualizar(any(), eq("admin"))).thenReturn(new ServicioNiveles.Resultado(new ConfiguracionNiveles(
                2, 6, ReglasNivelesTest.iniciales(), null, "admin", OffsetDateTime.now()), 4));

        mvc.perform(administrador(put(URL)).content(cuerpo(499, 500, 6)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuracion.version").value(2))
                .andExpect(jsonPath("$.clientesReclasificados").value(4));
    }

    @Test
    void unosUmbralesQueSeSuperponenSeRechazanIndicandoElConflicto() throws Exception {
        // Servicio real: la regla de superposición está en ReglasNiveles, antes de tocar la base.
        ServicioNiveles real = new ServicioNiveles(mock(ConfiguracionesNiveles.class), mock(AsignacionNiveles.class));
        LocalValidatorFactoryBean validador = new LocalValidatorFactoryBean();
        validador.afterPropertiesSet();
        MockMvc conServicioReal = MockMvcBuilders.standaloneSetup(new NivelesController(real, new Validaciones(validador)))
                .setControllerAdvice(new ManejadorDeErrores()).build();

        conServicioReal.perform(administrador(put(URL)).content(cuerpo(500, 500, 12)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("se superponen")))
                .andExpect(jsonPath("$.campos['PLATA.puntosMinimos']").value(containsString("debe ser 501")));
    }

    @Test
    void unUmbralNegativoOUnPeriodoInvalidoSeRechazanIndicandoElCampo() throws Exception {
        mvc.perform(administrador(put(URL)).content("""
                        {"periodoMeses": 0, "version": 1, "niveles": [
                          {"nivel": "BRONCE", "puntosMinimos": -1, "puntosMaximos": 499},
                          {"nivel": "PLATA", "puntosMinimos": 500, "puntosMaximos": 1999},
                          {"nivel": "ORO", "puntosMinimos": 2000}]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.periodoMeses").value("debe ser de al menos 1 mes"))
                .andExpect(jsonPath("$.campos['niveles[0].puntosMinimos']").value("no puede ser negativo"));
        verify(servicio, never()).actualizar(any(), anyString());
    }

    @Test
    void unRolDistintoDelAdministradorNoPuedeCambiarNiReclasificar() throws Exception {
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(cuerpo(499, 500, 12))
                        .header("X-Usuario", "agente").header("X-Usuario-Rol", "AGENTE_ATENCION")
                        .header("X-Usuario-Permisos", "PUNTOS_CONSULTAR,FIDELIZACION_CONFIGURAR"))
                .andExpect(status().isForbidden());
        mvc.perform(post(URL + "/recalcular").header("X-Usuario", "gerente").header("X-Usuario-Rol", "GERENTE_COMERCIAL")
                        .header("X-Usuario-Permisos", "PUNTOS_CONSULTAR"))
                .andExpect(status().isForbidden());
        mvc.perform(get(URL + "/historial")).andExpect(status().isUnauthorized());
        verify(servicio, never()).actualizar(any(), anyString());
        verify(servicio, never()).recalcularTodos();
    }

    @Test
    void elAdministradorPuedeReclasificarATodos() throws Exception {
        when(servicio.recalcularTodos()).thenReturn(3);

        mvc.perform(administrador(post(URL + "/recalcular")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientesReclasificados").value(3));
    }

    private static MockHttpServletRequestBuilder administrador(MockHttpServletRequestBuilder solicitud) {
        return solicitud.contentType(MediaType.APPLICATION_JSON).header("X-Usuario", "admin")
                .header("X-Usuario-Rol", "ADMINISTRADOR_CRM")
                .header("X-Usuario-Permisos", "PUNTOS_CONSULTAR,FIDELIZACION_CONFIGURAR");
    }

    private static String cuerpo(long maximoBronce, long minimoPlata, int periodo) {
        return """
                {"periodoMeses": %d, "version": 1, "niveles": [
                  {"nivel": "BRONCE", "puntosMinimos": 0, "puntosMaximos": %d},
                  {"nivel": "PLATA", "puntosMinimos": %d, "puntosMaximos": 1999},
                  {"nivel": "ORO", "puntosMinimos": 2000, "puntosMaximos": null}]}
                """.formatted(periodo, maximoBronce, minimoPlata);
    }
}
