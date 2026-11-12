package com.maxiconecta.crm.fidelizacion.parametro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.maxiconecta.crm.fidelizacion.movimiento.ClientesPerfil;
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
 * SCRUM-520 · API de los parámetros generales de servicio-fidelizacion: solo el Administrador de CRM con
 * PARAMETROS_CONFIGURAR los consulta y cambia, el acceso se comprueba antes del contenido, un valor fuera de rango
 * se rechaza con el motivo (criterio 2) y un cambio no pisa el de otro usuario.
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ParametrosControllerIntegracionTest {

    private static final String URL = "/api/fidelizacion/parametros";
    private static final String PERMISOS_ADMIN = "PUNTOS_CONSULTAR,FIDELIZACION_CONFIGURAR,PARAMETROS_CONFIGURAR";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private ClientesPerfil perfiles;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private ParametrosGenerales parametros;

    @BeforeEach
    void preparar() {
        Parametro actual = parametro(CodigosParametros.PUNTOS_VIGENCIA_MESES);
        if (actual.valor().intValue() != 12) {
            parametros.actualizar(List.of(new CambioParametro(actual.codigo(), BigDecimal.valueOf(12),
                    actual.version())), "admin");
        }
    }

    @Test
    void elAdministradorConsultaLosParametrosConSuRangoYVersion() throws Exception {
        mvc.perform(como("admin", "ADMINISTRADOR_CRM", PERMISOS_ADMIN, get(URL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].codigo").value(CodigosParametros.PUNTOS_VIGENCIA_MESES))
                .andExpect(jsonPath("$[0].unidad").value("meses"))
                .andExpect(jsonPath("$[0].minimo").value(1))
                .andExpect(jsonPath("$[0].maximo").value(120))
                .andExpect(jsonPath("$[0].decimales").value(0));
    }

    @Test
    void elAdministradorGuardaUnCambioYRecibeLaVersionNueva() throws Exception {
        long version = parametro(CodigosParametros.PUNTOS_VIGENCIA_MESES).version();

        JsonNode respuesta = json.readTree(mvc.perform(como("admin", "ADMINISTRADOR_CRM", PERMISOS_ADMIN, put(URL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cambio(CodigosParametros.PUNTOS_VIGENCIA_MESES, "24", version)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(respuesta.get(0).get("valor").asInt()).isEqualTo(24);
        assertThat(respuesta.get(0).get("version").asLong()).isEqualTo(version + 1);
        assertThat(respuesta.get(0).get("modificadoPor").asText()).isEqualTo("admin");
    }

    @Test
    void unValorFueraDeRangoSeRechazaConElMotivoYNoSeGuarda() throws Exception {
        long version = parametro(CodigosParametros.PUNTOS_VIGENCIA_MESES).version();

        mvc.perform(como("admin", "ADMINISTRADOR_CRM", PERMISOS_ADMIN, put(URL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cambio(CodigosParametros.PUNTOS_VIGENCIA_MESES, "121", version)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.PUNTOS_VIGENCIA_MESES").value("debe estar entre 1 y 120 meses"));

        assertThat(parametro(CodigosParametros.PUNTOS_VIGENCIA_MESES).valor().intValue()).isEqualTo(12);
    }

    @Test
    void unaVersionVencidaDevuelve409() throws Exception {
        long version = parametro(CodigosParametros.PUNTOS_VIGENCIA_MESES).version();
        parametros.actualizar(List.of(new CambioParametro(CodigosParametros.PUNTOS_VIGENCIA_MESES, BigDecimal.valueOf(18),
                version)), "otro-admin");

        mvc.perform(como("admin", "ADMINISTRADOR_CRM", PERMISOS_ADMIN, put(URL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cambio(CodigosParametros.PUNTOS_VIGENCIA_MESES, "24", version)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campos.PUNTOS_VIGENCIA_MESES").exists());
    }

    @Test
    void sinIdentidadDelGatewayEs401YOtroRolOSinPermisoEs403AntesDeValidar() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        for (MockHttpServletRequestBuilder solicitud : List.of(
                como("ana", "GERENTE_COMERCIAL", "PUNTOS_CONSULTAR,PARAMETROS_CONFIGURAR", get(URL)),
                como("admin", "ADMINISTRADOR_CRM", "FIDELIZACION_CONFIGURAR", get(URL)),
                como("luis", "AGENTE_ATENCION", "PUNTOS_CONSULTAR", put(URL))
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
