package com.maxiconecta.crm.gateway.seguridad;

import com.maxiconecta.crm.gateway.auditoria.AuditoriaService;
import com.maxiconecta.crm.gateway.rol.RolService;
import com.maxiconecta.crm.gateway.usuario.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-548 · Token de servicio de Marketplace y Ventas y control de acceso de RIO-CRM-04 en el Gateway
 * (docs/contratos-eventos/RIO-CRM-04-consulta-puntos.md). La ruta de fidelización la responde un controlador
 * de prueba que devuelve la identidad que recibiría el microservicio.
 */
@WebMvcTest
@Import({SecurityConfig.class, JwtService.class, TokenServicioTest.FidelizacionSimulada.class})
@TestPropertySource(properties = {
        "crm.seguridad.jwt.secreto=secreto-de-prueba-con-mas-de-32-caracteres-crm",
        "spring.cloud.gateway.mvc.enabled=false",
        "crm.seguridad.servicios.clientes.marketplace-ventas.nombre=Marketplace y Ventas",
        "crm.seguridad.servicios.clientes.marketplace-ventas.secreto=" + TokenServicioTest.SECRETO,
        "crm.seguridad.servicios.clientes.marketplace-ventas.permisos=PUNTOS_CONSULTAR_VENTAS",
        "crm.seguridad.servicios.clientes.secreto-corto.secreto=corto",
        "crm.seguridad.servicios.clientes.secreto-corto.permisos=PUNTOS_CONSULTAR_VENTAS"
})
class TokenServicioTest {

    static final String SECRETO = "secreto-de-ventas-de-prueba-con-32-caracteres";
    private static final String RUTA = "/api/fidelizacion/ventas/clientes/CLI-5521/puntos";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private UsuarioService usuarioService;

    @MockBean
    private RolService rolService;

    @MockBean
    private AuditoriaService auditoriaService;

    // --- Emisión del token de servicio ---

    @Test
    void conElClienteYElSecretoCorrectosEmiteElTokenDeServicio() throws Exception {
        mvc.perform(post("/api/auth/servicio/token").contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales("marketplace-ventas", SECRETO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEn").isNotEmpty())
                .andExpect(jsonPath("$.cliente").value("marketplace-ventas"))
                .andExpect(jsonPath("$.permisos[0]").value("PUNTOS_CONSULTAR_VENTAS"));
    }

    @Test
    void unSecretoIncorrectoYUnClienteDesconocidoDevuelvenElMismo401() throws Exception {
        for (String cuerpo : List.of(credenciales("marketplace-ventas", SECRETO + "x"),
                credenciales("otro-sistema", SECRETO))) {
            mvc.perform(post("/api/auth/servicio/token").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.mensaje").value("Cliente de servicio o secreto incorrectos"));
        }
    }

    @Test
    void unClienteConUnSecretoDeMenosDe32CaracteresQuedaDeshabilitado() throws Exception {
        mvc.perform(post("/api/auth/servicio/token").contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales("secreto-corto", "corto")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sinClienteOSecretoResponde400() throws Exception {
        mvc.perform(post("/api/auth/servicio/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cliente\":\"marketplace-ventas\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- Criterio 1: con un token de servicio válido la consulta llega a fidelización ---

    @Test
    void conElTokenDeServicioLaConsultaPasaYLlevaLaIdentidadDelSistema() throws Exception {
        mvc.perform(get(RUTA).header("Authorization", tokenDeVentas())
                        // Una identidad falsificada por el cliente se descarta.
                        .header("X-Usuario-Rol", "ADMINISTRADOR_CRM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("marketplace-ventas"))
                .andExpect(jsonPath("$.rol").value("SISTEMA"))
                .andExpect(jsonPath("$.permisos").value("PUNTOS_CONSULTAR_VENTAS"));
    }

    // --- Criterio 2: sin token o con un token inválido, 401 y la consulta no se procesa ---

    @Test
    void sinTokenOConUnTokenInvalidoResponde401() throws Exception {
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        mvc.perform(get(RUTA).header("Authorization", "Bearer token-invalido")).andExpect(status().isUnauthorized());
        mvc.perform(get(RUTA).header("Authorization", tokenDeVentas() + "alterado")).andExpect(status().isUnauthorized());
    }

    @Test
    void elTokenDeUnClienteQueYaNoEstaHabilitadoResponde401() throws Exception {
        String token = jwtService.emitirServicio(new SesionToken("sistema-retirado", "Retirado", ClientesServicio.ROL,
                List.of("PUNTOS_CONSULTAR_VENTAS"))).token();

        mvc.perform(get(RUTA).header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenDeUsuarioConElNombreDelSistemaNoObtieneSusPermisos() throws Exception {
        // Token de usuario interno (sin tipo=SERVICIO) con los datos del sistema: se valida contra los usuarios.
        String token = jwtService.emitir("marketplace-ventas", "falso", ClientesServicio.ROL,
                List.of("PUNTOS_CONSULTAR_VENTAS")).token();
        when(usuarioService.sesionVigente(anyString())).thenReturn(Optional.empty());

        mvc.perform(get(RUTA).header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void unUsuarioInternoConPermisoDePuntosNoUsaLaConsultaDeVentas() throws Exception {
        List<String> permisos = List.of("CLIENTE_CONSULTAR", "PUNTOS_CONSULTAR", "FIDELIZACION_CONFIGURAR");
        when(usuarioService.sesionVigente("admin"))
                .thenReturn(Optional.of(new SesionToken("admin", "admin", "ADMINISTRADOR_CRM", permisos)));
        String token = jwtService.emitir("admin", "admin", "ADMINISTRADOR_CRM", permisos).token();

        mvc.perform(get(RUTA).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    @Test
    void elTokenDeServicioNoAbreOtrasRutasDelCrm() throws Exception {
        String ventas = tokenDeVentas();
        for (String ruta : List.of("/api/fidelizacion/clientes/42/puntos", "/api/fidelizacion/clientes/42/saldo",
                "/api/perfil/clientes/42", "/api/admin/usuarios")) {
            mvc.perform(get(ruta).header("Authorization", ventas)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/fidelizacion/reglas").header("Authorization", ventas)).andExpect(status().isForbidden());
    }

    // --- Criterio 4: el Gateway registra cada consulta en la auditoría ---

    @Test
    void laConsultaDeVentasQuedaRegistradaConElSistemaYElClienteDeVentas() throws Exception {
        mvc.perform(get(RUTA).header("Authorization", tokenDeVentas())).andExpect(pasa());

        verify(auditoriaService).registrar(eq("marketplace-ventas"), eq(AuditoriaService.CLIENTE_CONSULTADO),
                eq(AuditoriaService.ENTIDAD_CLIENTE), eq("CLI-5521"), argThat(detalle -> detalle.contains("HTTP 200")));
    }

    @Test
    void unaConsultaSinTokenQuedaRegistradaComoAccesoDenegado() throws Exception {
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());

        verify(auditoriaService).registrar(eq("anonimo"), eq(AuditoriaService.ACCESO_DENEGADO),
                eq(AuditoriaService.ENTIDAD_CLIENTE), eq("CLI-5521"), argThat(detalle -> detalle.contains("HTTP 401")));
    }

    private String tokenDeVentas() throws Exception {
        String respuesta = mvc.perform(post("/api/auth/servicio/token").contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales("marketplace-ventas", SECRETO)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = respuesta.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        assertThat(token).doesNotContain("{");
        return "Bearer " + token;
    }

    private static String credenciales(String cliente, String secreto) {
        return "{\"cliente\":\"" + cliente + "\",\"secreto\":\"" + secreto + "\"}";
    }

    private static ResultMatcher pasa() {
        return resultado -> assertThat(resultado.getResponse().getStatus()).isNotIn(401, 403);
    }

    /** Hace de servicio-fidelizacion: devuelve la identidad que el Gateway le reenvía. */
    @RestController
    static class FidelizacionSimulada {
        @GetMapping("/api/fidelizacion/ventas/clientes/{idClienteVentas}/puntos")
        public Map<String, String> puntos(@PathVariable String idClienteVentas, HttpServletRequest solicitud) {
            return Map.of("usuario", String.valueOf(solicitud.getHeader("X-Usuario")),
                    "rol", String.valueOf(solicitud.getHeader("X-Usuario-Rol")),
                    "permisos", String.valueOf(solicitud.getHeader("X-Usuario-Permisos")));
        }
    }
}
