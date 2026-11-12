package com.maxiconecta.crm.comportamiento.parametro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-520 · API de los parámetros generales de servicio-comportamiento: solo el Administrador de CRM con
 * PARAMETROS_CONFIGURAR los consulta y cambia, el acceso se comprueba antes del contenido, un valor fuera de rango
 * se rechaza con el motivo (criterio 2) y un cambio no pisa el de otro usuario.
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ParametrosControllerIntegracionTest {

    private static final String URL = "/api/comportamiento/parametros";
    private static final String PERMISOS_ADMIN = "CLIENTE_CONSULTAR,INDICADORES_CONSULTAR,PARAMETROS_CONFIGURAR";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private ParametrosGenerales parametros;

    @BeforeEach
    void preparar() {
        Parametro actual = parametro(CodigosParametros.INACTIVIDAD_DIAS);
        if (actual.valor().intValue() != 90) {
            parametros.actualizar(List.of(new CambioParametro(actual.codigo(), BigDecimal.valueOf(90),
                    actual.version())), "admin");
        }
    }

    @Test
    void elAdministradorConsultaLosParametrosConSuRangoYVersion() throws Exception {
        mvc.perform(como("admin", "ADMINISTRADOR_CRM", PERMISOS_ADMIN, get(URL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].codigo").value(CodigosParametros.INACTIVIDAD_DIAS))
                .andExpect(jsonPath("$[0].unidad").value("días"))
                .andExpect(jsonPath("$[0].minimo").value(7))
                .andExpect(jsonPath("$[0].maximo").value(730))
                .andExpect(jsonPath("$[1].codigo").value(CodigosParametros.NIVEL_CONSUMO_UMBRAL_MEDIO))
                .andExpect(jsonPath("$[2].codigo").value(CodigosParametros.NIVEL_CONSUMO_UMBRAL_ALTO))
                .andExpect(jsonPath("$[2].decimales").value(2));
    }

    @Test
    void elAdministradorGuardaUnCambioYRecibeLaVersionNueva() throws Exception {
        long version = parametro(CodigosParametros.INACTIVIDAD_DIAS).version();

        JsonNode respuesta = json.readTree(mvc.perform(como("admin", "ADMINISTRADOR_CRM", PERMISOS_ADMIN, put(URL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cambio(CodigosParametros.INACTIVIDAD_DIAS, "120", version)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(respuesta.get(0).get("valor").asInt()).isEqualTo(120);
        assertThat(respuesta.get(0).get("version").asLong()).isEqualTo(version + 1);
        assertThat(respuesta.get(0).get("modificadoPor").asText()).isEqualTo("admin");
    }

    @Test
    void unValorFueraDeRangoSeRechazaConElMotivoYNoSeGuarda() throws Exception {
        long version = parametro(CodigosParametros.INACTIVIDAD_DIAS).version();

        mvc.perform(como("admin", "ADMINISTRADOR_CRM", PERMISOS_ADMIN, put(URL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cambio(CodigosParametros.INACTIVIDAD_DIAS, "3", version)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.INACTIVIDAD_DIAS").value("debe estar entre 7 y 730 días"));

        assertThat(parametro(CodigosParametros.INACTIVIDAD_DIAS).valor().intValue()).isEqualTo(90);
    }

    @Test
    void unaVersionVencidaDevuelve409() throws Exception {
        long version = parametro(CodigosParametros.INACTIVIDAD_DIAS).version();
        parametros.actualizar(List.of(new CambioParametro(CodigosParametros.INACTIVIDAD_DIAS, BigDecimal.valueOf(100),
                version)), "otro-admin");

        mvc.perform(como("admin", "ADMINISTRADOR_CRM", PERMISOS_ADMIN, put(URL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cambio(CodigosParametros.INACTIVIDAD_DIAS, "120", version)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campos.INACTIVIDAD_DIAS").exists());
    }

    @Test
    void sinIdentidadDelGatewayEs401YOtroRolOSinPermisoEs403AntesDeValidar() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        for (MockHttpServletRequestBuilder solicitud : List.of(
                como("ana", "GERENTE_COMERCIAL", "CLIENTE_CONSULTAR,PARAMETROS_CONFIGURAR", get(URL)),
                como("admin", "ADMINISTRADOR_CRM", "CLIENTE_CONSULTAR", get(URL)),
                como("luis", "AGENTE_ATENCION", "CLIENTE_CONSULTAR", put(URL))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cambios\":[]}"))) {
            mvc.perform(solicitud).andExpect(status().isForbidden());
        }
    }

    private Parametro parametro(String codigo) {
        return parametros.listar().stream().filter(p -> p.codigo().equals(codigo)).findFirst().orElseThrow();
    }

    private static String cambio(String codigo, String valor, long version) {
        return "{\"cambios\":[{\"codigo\":\"" + codigo + "\",\"valor\":" + valor + ",\"version\":" + version + "}]}";
    }

    private static MockHttpServletRequestBuilder como(String usuario, String rol, String permisos,
                                                      MockHttpServletRequestBuilder solicitud) {
        return solicitud.header("X-Usuario", usuario).header("X-Usuario-Rol", rol)
                .header("X-Usuario-Permisos", permisos);
    }
}
