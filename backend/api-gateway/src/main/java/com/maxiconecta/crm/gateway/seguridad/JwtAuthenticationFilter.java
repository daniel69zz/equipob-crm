package com.maxiconecta.crm.gateway.seguridad;

import com.maxiconecta.crm.gateway.usuario.UsuarioService;
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
 * Lee el token "Authorization: Bearer ..." y, si es válido, autentica la petición.
 * El token solo identifica al usuario: el rol y los permisos se leen de la base en cada
 * petición, para que un cambio de rol o una desactivación rijan de inmediato.
 * Un token ausente, inválido o de un usuario desactivado deja la petición sin autenticar (401).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioService usuarioService;

    public JwtAuthenticationFilter(JwtService jwtService, UsuarioService usuarioService) {
        this.jwtService = jwtService;
        this.usuarioService = usuarioService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String cabecera = solicitud.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecera != null && cabecera.startsWith(PREFIJO)) {
            jwtService.validar(cabecera.substring(PREFIJO.length()))
                    .flatMap(token -> usuarioService.sesionVigente(token.usuario()))
                    .ifPresent(sesion -> {
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
