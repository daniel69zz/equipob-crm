package com.maxiconecta.crm.gateway.auditoria;

import com.maxiconecta.crm.gateway.rol.RolService;
import com.maxiconecta.crm.gateway.seguridad.JwtService;
import com.maxiconecta.crm.gateway.seguridad.SecurityConfig;
import com.maxiconecta.crm.gateway.seguridad.SesionToken;
import com.maxiconecta.crm.gateway.usuario.UsuarioService;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-514 · Trazabilidad de la auditoría según docs/seguridad/alcance-auditoria.md.
 */
@WebMvcTest
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "crm.seguridad.jwt.secreto=secreto-de-prueba-con-mas-de-32-caracteres-crm",
        "spring.cloud.gateway.mvc.enabled=false"
})
class TrazabilidadAuditoriaTest {

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

    @BeforeEach
    void consultaSinResultados() {
        when(auditoriaService.buscar(any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of()));
    }

    // --- Criterio 1: consultas y modificaciones quedan registradas ---

    @Test
    void unaConsultaDeClienteQuedaRegistradaConUsuarioOperacionYCliente() throws Exception {
        mvc.perform(get("/api/perfil/clientes/42").header("Authorization", bearer("ana", "AGENTE_ATENCION", PERMISOS_AGENTE)));

        verify(auditoriaService).registrar(eq("ana"), eq(AuditoriaService.CLIENTE_CONSULTADO),
                eq(AuditoriaService.ENTIDAD_CLIENTE), eq("42"),
                argThat(detalle -> detalle.startsWith("GET /api/perfil/clientes/42 · HTTP ")));
    }

    @Test
    void unaModificacionDeClienteQuedaRegistrada() throws Exception {
        mvc.perform(put("/api/perfil/clientes/42/consentimiento").header("Authorization", bearerAdministrador()));

        verify(auditoriaService).registrar(eq("admin"), eq(AuditoriaService.CLIENTE_MODIFICADO),
                eq(AuditoriaService.ENTIDAD_CLIENTE), eq("42"), anyString());
    }

    @Test
    void unaConsultaDeLaFichaIntegralQuedaRegistradaConElClienteDeLaRuta() throws Exception {
        List<String> permisosConFichaIntegral = List.of("CLIENTE_CONSULTAR", "INDICADORES_CONSULTAR",
                "PUNTOS_CONSULTAR", "INTERACCIONES_CONSULTAR", "INTERACCIONES_REGISTRAR", "FICHA_INTEGRAL_CONSULTAR");

        mvc.perform(get("/api/perfil/clientes/42/ficha-integral")
                .header("Authorization", bearer("ana", "AGENTE_ATENCION", permisosConFichaIntegral)));

        verify(auditoriaService).registrar(eq("ana"), eq(AuditoriaService.CLIENTE_CONSULTADO),
                eq(AuditoriaService.ENTIDAD_CLIENTE), eq("42"),
                argThat(detalle -> detalle.startsWith("GET /api/perfil/clientes/42/ficha-integral · HTTP ")));
    }

    @Test
    void unIntentoDeConsultarLaFichaIntegralSinElPermisoQuedaRegistradoComoAccesoDenegado() throws Exception {
        mvc.perform(get("/api/perfil/clientes/42/ficha-integral")
                        .header("Authorization", bearer("luis", "GERENTE_COMERCIAL", PERMISOS_GERENTE)))
                .andExpect(status().isForbidden());

        verify(auditoriaService).registrar(eq("luis"), eq(AuditoriaService.ACCESO_DENEGADO),
                eq(AuditoriaService.ENTIDAD_CLIENTE), eq("42"), argThat(detalle -> detalle.contains("HTTP 403")));
    }

    @Test
    void lasConsultasEnOtrosMicroserviciosTambienSeRegistran() throws Exception {
        mvc.perform(get("/api/fidelizacion/clientes/42/puntos")
                .header("Authorization", bearer("luis", "GERENTE_COMERCIAL", PERMISOS_GERENTE)));

        verify(auditoriaService).registrar(eq("luis"), eq(AuditoriaService.CLIENTE_CONSULTADO),
                eq(AuditoriaService.ENTIDAD_CLIENTE), eq("42"), anyString());
    }

    @Test
    void unaBusquedaSeRegistraSinGuardarLosParametros() throws Exception {
        mvc.perform(get("/api/perfil/clientes").param("documento", "4455667")
                .header("Authorization", bearer("ana", "AGENTE_ATENCION", PERMISOS_AGENTE)));

        verify(auditoriaService).registrar(eq("ana"), eq(AuditoriaService.CLIENTE_CONSULTADO),
                eq(AuditoriaService.ENTIDAD_CLIENTE), isNull(),
                argThat(detalle -> detalle.startsWith("GET /api/perfil/clientes · ") && !detalle.contains("4455667")));
    }

    // --- Criterio 2: los intentos denegados también quedan registrados ---

    @Test
    void unIntentoSinPermisoQuedaRegistradoComoAccesoDenegado() throws Exception {
        mvc.perform(post("/api/perfil/clientes/42").header("Authorization", bearer("luis", "GERENTE_COMERCIAL", PERMISOS_GERENTE)))
                .andExpect(status().isForbidden());

        verify(auditoriaService).registrar(eq("luis"), eq(AuditoriaService.ACCESO_DENEGADO),
                eq(AuditoriaService.ENTIDAD_CLIENTE), eq("42"), argThat(detalle -> detalle.contains("HTTP 403")));
    }

    @Test
    void unIntentoSinSesionQuedaRegistradoComoAnonimo() throws Exception {
        mvc.perform(get("/api/perfil/clientes/42")).andExpect(status().isUnauthorized());

        verify(auditoriaService).registrar(eq(AuditoriaAccesoFilter.ANONIMO), eq(AuditoriaService.ACCESO_DENEGADO),
                eq(AuditoriaService.ENTIDAD_CLIENTE), eq("42"), argThat(detalle -> detalle.contains("HTTP 401")));
    }

    @Test
    void lasRutasQueNoSonDeUnClienteNoSeRegistranComoAcceso() throws Exception {
        mvc.perform(get("/api/admin/usuarios").header("Authorization", bearerAdministrador()));
        mvc.perform(get("/api/segmentacion/segmentos").header("Authorization", bearerAdministrador()));

        verify(auditoriaService, never()).registrar(anyString(), anyString(),
                eq(AuditoriaService.ENTIDAD_CLIENTE), any(), anyString());
    }

    @Test
    void unFalloAlRegistrarNoCambiaLaRespuestaAlUsuario() throws Exception {
        doThrow(new IllegalStateException("base no disponible"))
                .when(auditoriaService).registrar(anyString(), anyString(), anyString(), any(), anyString());

        mvc.perform(post("/api/perfil/clientes/42").header("Authorization", bearer("luis", "GERENTE_COMERCIAL", PERMISOS_GERENTE)))
                .andExpect(status().isForbidden());
    }

    // --- Criterio 3: los registros son de solo lectura ---

    @Test
    void elApiNoPermiteModificarNiBorrarRegistros() throws Exception {
        mvc.perform(put("/api/admin/auditoria").header("Authorization", bearerAdministrador()))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/admin/auditoria").header("Authorization", bearerAdministrador()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void laEntidadDeAuditoriaEsInmutable() {
        assertThat(EventoAuditoria.class.isAnnotationPresent(Immutable.class)).isTrue();
    }

    // --- Criterio 4: consulta con filtros, solo para quien tiene AUDITORIA_CONSULTAR ---

    @Test
    void elAdministradorFiltraPorUsuarioFechaYOperacion() throws Exception {
        mvc.perform(get("/api/admin/auditoria").header("Authorization", bearerAdministrador())
                        .param("usuario", "ana").param("operacion", "ACCESO_DENEGADO")
                        .param("desde", "2026-09-01").param("hasta", "2026-09-30").param("pagina", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventos").isArray());

        ArgumentCaptor<FiltroAuditoria> filtro = ArgumentCaptor.forClass(FiltroAuditoria.class);
        verify(auditoriaService).buscar(filtro.capture(), eq(2), eq(50));
        assertThat(filtro.getValue()).isEqualTo(new FiltroAuditoria("ana", "ACCESO_DENEGADO", null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
    }

    @Test
    void unRangoDeFechasInvertidoSeRechaza() throws Exception {
        mvc.perform(get("/api/admin/auditoria").header("Authorization", bearerAdministrador())
                        .param("desde", "2026-09-30").param("hasta", "2026-09-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elAgenteNoPuedeConsultarLaAuditoria() throws Exception {
        mvc.perform(get("/api/admin/auditoria").header("Authorization", bearer("ana", "AGENTE_ATENCION", PERMISOS_AGENTE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void laConsultaOfreceLasOperacionesParaFiltrar() throws Exception {
        mvc.perform(get("/api/admin/auditoria/operaciones").header("Authorization", bearerAdministrador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(AuditoriaService.CLIENTE_CONSULTADO));
    }

    // --- Utilidades ---

    private String bearerAdministrador() {
        return bearer("admin", "ADMINISTRADOR_CRM", PERMISOS_ADMINISTRADOR);
    }

    private String bearer(String usuario, String rol, List<String> permisos) {
        when(usuarioService.sesionVigente(usuario))
                .thenReturn(Optional.of(new SesionToken(usuario, usuario, rol, permisos)));
        return "Bearer " + jwtService.emitir(usuario, usuario, rol, permisos).token();
    }
}
