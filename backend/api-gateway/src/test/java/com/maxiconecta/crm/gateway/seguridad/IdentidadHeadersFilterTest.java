package com.maxiconecta.crm.gateway.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IdentidadHeadersFilterTest {

    private final IdentidadHeadersFilter filtro = new IdentidadHeadersFilter();

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reemplazaLasCabecerasFalsificadasPorLaIdentidadDelToken() throws Exception {
        UsernamePasswordAuthenticationToken autenticacion =
                new UsernamePasswordAuthenticationToken("ana", null, List.of());
        autenticacion.setDetails(new SesionToken("ana", "Ana Perez", "AGENTE_ATENCION",
                List.of("CLIENTE_CONSULTAR", "PUNTOS_CONSULTAR")));
        SecurityContextHolder.getContext().setAuthentication(autenticacion);

        MockHttpServletRequest solicitud = new MockHttpServletRequest("GET", "/api/perfil/clientes/1");
        solicitud.addHeader("X-Usuario", "admin");
        solicitud.addHeader("X-Usuario-Rol", "ADMINISTRADOR_CRM");

        HttpServletRequest reenviada = aplicarFiltro(solicitud);

        assertThat(reenviada.getHeader("X-Usuario")).isEqualTo("ana");
        assertThat(reenviada.getHeader("X-Usuario-Rol")).isEqualTo("AGENTE_ATENCION");
        assertThat(reenviada.getHeader("X-Usuario-Permisos")).isEqualTo("CLIENTE_CONSULTAR,PUNTOS_CONSULTAR");
    }

    @Test
    void descartaLasCabecerasDeIdentidadSiNoHayUsuarioAutenticado() throws Exception {
        MockHttpServletRequest solicitud = new MockHttpServletRequest("GET", "/actuator/health");
        solicitud.addHeader("X-Usuario", "admin");
        solicitud.addHeader("X-Usuario-Rol", "ADMINISTRADOR_CRM");
        solicitud.addHeader("Accept", "application/json");

        HttpServletRequest reenviada = aplicarFiltro(solicitud);

        assertThat(reenviada.getHeader("X-Usuario")).isNull();
        assertThat(reenviada.getHeader("X-Usuario-Rol")).isNull();
        assertThat(reenviada.getHeader("Accept")).isEqualTo("application/json");
    }

    private HttpServletRequest aplicarFiltro(MockHttpServletRequest solicitud) throws Exception {
        MockFilterChain cadena = new MockFilterChain();
        filtro.doFilter(solicitud, new MockHttpServletResponse(), cadena);
        return (HttpServletRequest) cadena.getRequest();
    }
}
