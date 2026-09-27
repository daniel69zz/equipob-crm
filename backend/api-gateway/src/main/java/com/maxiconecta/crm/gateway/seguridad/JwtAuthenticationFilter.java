package com.maxiconecta.crm.gateway.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lee el token "Authorization: Bearer ..." y, si es válido, autentica la petición
 * con el rol (ROLE_...) y los permisos del usuario como autoridades.
 * Un token ausente o inválido deja la petición sin autenticar: la regla de acceso responde 401.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String cabecera = solicitud.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecera != null && cabecera.startsWith(PREFIJO)) {
            jwtService.validar(cabecera.substring(PREFIJO.length())).ifPresent(sesion -> {
                List<GrantedAuthority> autoridades = new ArrayList<>();
                autoridades.add(new SimpleGrantedAuthority("ROLE_" + sesion.rol()));
                sesion.permisos().forEach(permiso -> autoridades.add(new SimpleGrantedAuthority(permiso)));
                UsernamePasswordAuthenticationToken autenticacion =
                        new UsernamePasswordAuthenticationToken(sesion.usuario(), null, autoridades);
                autenticacion.setDetails(sesion);
                SecurityContextHolder.getContext().setAuthentication(autenticacion);
            });
        }
        cadena.doFilter(solicitud, respuesta);
    }
}
