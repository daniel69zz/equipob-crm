package com.maxiconecta.crm.gateway.seguridad;

import com.maxiconecta.crm.gateway.auditoria.AuditoriaService;
import com.maxiconecta.crm.gateway.rol.Permiso;
import com.maxiconecta.crm.gateway.rol.Rol;
import com.maxiconecta.crm.gateway.rol.RolService;
import com.maxiconecta.crm.gateway.usuario.Usuario;
import com.maxiconecta.crm.gateway.usuario.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-509 · Casos de acceso por perfil según docs/seguridad/matriz-permisos.md.
 */
@WebMvcTest
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "crm.seguridad.jwt.secreto=secreto-de-prueba-con-mas-de-32-caracteres-crm",
        "spring.cloud.gateway.mvc.enabled=false"
})
class AccesoPorRolTest {

    private static final List<String> PERMISOS_ADMINISTRADOR = List.of(
            "CLIENTE_CONSULTAR", "CLIENTE_EDITAR", "INDICADORES_CONSULTAR", "EVENTOS_REPROCESAR",
            "SEGMENTOS_CONSULTAR", "SEGMENTACION_CONFIGURAR", "PUNTOS_CONSULTAR", "FIDELIZACION_CONFIGURAR",
            "INTERACCIONES_CONSULTAR", "INTERACCIONES_REGISTRAR", "USUARIOS_ADMINISTRAR", "AUDITORIA_CONSULTAR");
    private static final List<String> PERMISOS_AGENTE = List.of(
            "CLIENTE_CONSULTAR", "INDICADORES_CONSULTAR", "PUNTOS_CONSULTAR",
            "INTERACCIONES_CONSULTAR", "INTERACCIONES_REGISTRAR");
    private static final List<String> PERMISOS_GERENTE = List.of(
            "CLIENTE_CONSULTAR", "INDICADORES_CONSULTAR", "SEGMENTOS_CONSULTAR",
            "PUNTOS_CONSULTAR", "INTERACCIONES_CONSULTAR");

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

    // --- Inicio de sesión ---

    @Test
    void conCredencialesCorrectasDevuelveUnTokenConElRol() throws Exception {
        when(usuarioService.autenticar("ana", "clave-segura"))
                .thenReturn(Optional.of(usuario("ana", "AGENTE_ATENCION", PERMISOS_AGENTE)));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"ana\",\"password\":\"clave-segura\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.rol").value("AGENTE_ATENCION"));
    }

    @Test
    void conCredencialesIncorrectasDevuelve401ConMensajeClaro() throws Exception {
        when(usuarioService.autenticar("ana", "incorrecta")).thenReturn(Optional.empty());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"ana\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Usuario o contraseña incorrectos"));
    }

    @Test
    void laSesionActualSeLeeDelToken() throws Exception {
        mvc.perform(get("/api/auth/yo").header("Authorization", bearer("ana", "AGENTE_ATENCION", PERMISOS_AGENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("ana"))
                .andExpect(jsonPath("$.rol").value("AGENTE_ATENCION"));
    }

    // --- Peticiones sin token o con token inválido ---

    @Test
    void sinTokenDevuelve401() throws Exception {
        mvc.perform(get("/api/admin/usuarios")).andExpect(status().isUnauthorized());
    }

    @Test
    void conTokenInvalidoDevuelve401() throws Exception {
        mvc.perform(get("/api/admin/usuarios").header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
    }

    // --- Administración de usuarios y roles ---

    @Test
    void elAdministradorPuedeAdministrarUsuarios() throws Exception {
        mvc.perform(get("/api/admin/usuarios").header("Authorization", bearerAdministrador()))
                .andExpect(status().isOk());
    }

    @Test
    void elAgenteNoPuedeAdministrarUsuarios() throws Exception {
        mvc.perform(get("/api/admin/usuarios").header("Authorization", bearer("ana", "AGENTE_ATENCION", PERMISOS_AGENTE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void elGerenteNoPuedeAdministrarRoles() throws Exception {
        mvc.perform(get("/api/admin/roles").header("Authorization", bearer("luis", "GERENTE_COMERCIAL", PERMISOS_GERENTE)))
                .andExpect(status().isForbidden());
    }

    // --- Acceso a la información del cliente por perfil ---

    @Test
    void elAgentePuedeConsultarUnClientePeroNoModificarlo() throws Exception {
        String agente = bearer("ana", "AGENTE_ATENCION", PERMISOS_AGENTE);

        mvc.perform(get("/api/perfil/clientes/1").header("Authorization", agente)).andExpect(pasaElControlDeAcceso());
        mvc.perform(post("/api/perfil/clientes").header("Authorization", agente)).andExpect(status().isForbidden());
    }

    @Test
    void elAdministradorPuedeModificarClientes() throws Exception {
        mvc.perform(post("/api/perfil/clientes").header("Authorization", bearerAdministrador()))
                .andExpect(pasaElControlDeAcceso());
    }

    @Test
    void elGerenteConsultaInteraccionesPeroNoLasRegistra() throws Exception {
        String gerente = bearer("luis", "GERENTE_COMERCIAL", PERMISOS_GERENTE);

        mvc.perform(get("/api/interacciones/clientes/1").header("Authorization", gerente)).andExpect(pasaElControlDeAcceso());
        mvc.perform(post("/api/interacciones").header("Authorization", gerente)).andExpect(status().isForbidden());
    }

    @Test
    void soloElAdministradorPuedeReprocesarEventos() throws Exception {
        mvc.perform(post("/api/comportamiento/eventos/reproceso")
                        .header("Authorization", bearer("luis", "GERENTE_COMERCIAL", PERMISOS_GERENTE)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/comportamiento/eventos/reproceso").header("Authorization", bearerAdministrador()))
                .andExpect(pasaElControlDeAcceso());
    }

    @Test
    void unaRutaNoContempladaEnLaMatrizSeDeniega() throws Exception {
        mvc.perform(get("/api/desconocida").header("Authorization", bearerAdministrador()))
                .andExpect(status().isForbidden());
    }

    // --- Utilidades ---

    private static ResultMatcher pasaElControlDeAcceso() {
        return resultado -> assertThat(resultado.getResponse().getStatus()).isNotIn(401, 403);
    }

    private String bearerAdministrador() {
        return bearer("admin", "ADMINISTRADOR_CRM", PERMISOS_ADMINISTRADOR);
    }

    private String bearer(String usuario, String rol, List<String> permisos) {
        return "Bearer " + jwtService.emitir(usuario, usuario, rol, permisos).token();
    }

    private static Usuario usuario(String nombreUsuario, String codigoRol, List<String> permisos) {
        Set<Permiso> conjunto = permisos.stream()
                .map(codigo -> new Permiso(codigo, codigo))
                .collect(Collectors.toSet());
        Rol rol = new Rol(codigoRol, codigoRol, null, conjunto);
        return new Usuario(nombreUsuario, "Ana Perez", "hash-no-usado", rol);
    }
}
