package com.maxiconecta.crm.gateway.auditoria;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registra en la auditoría cada consulta o modificación de datos de un cliente, y también los
 * intentos denegados (401 y 403). Va antes del control de acceso para ver la respuesta final.
 * El alcance está en docs/seguridad/alcance-auditoria.md.
 */
public class AuditoriaAccesoFilter extends OncePerRequestFilter {

    public static final String ANONIMO = "anonimo";

    private static final Logger log = LoggerFactory.getLogger(AuditoriaAccesoFilter.class);

    /** /api/{servicio}/clientes[/{clienteId}[/...]] */
    private static final Pattern RUTA_DE_CLIENTE = Pattern.compile(
            "^/api/(?:perfil|comportamiento|segmentacion|fidelizacion|interacciones)/clientes(?:/([^/]+))?(?:/.*)?$");

    private static final int LARGO_MAXIMO_CLIENTE_ID = 60;

    private final AuditoriaService auditoriaService;

    public AuditoriaAccesoFilter(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest solicitud) {
        return HttpMethod.OPTIONS.matches(solicitud.getMethod()) || !RUTA_DE_CLIENTE.matcher(ruta(solicitud)).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        try {
            cadena.doFilter(solicitud, respuesta);
        } finally {
            registrar(solicitud, respuesta.getStatus());
        }
    }

    private void registrar(HttpServletRequest solicitud, int estado) {
        String ruta = ruta(solicitud);
        String detalle = "%s %s · HTTP %d · IP %s".formatted(solicitud.getMethod(), ruta, estado, solicitud.getRemoteAddr());
        try {
            auditoriaService.registrar(usuarioActual(), operacion(solicitud.getMethod(), estado),
                    AuditoriaService.ENTIDAD_CLIENTE, clienteId(ruta), detalle);
        } catch (RuntimeException ex) {
            log.error("No se pudo registrar en la auditoría: {}", detalle, ex);
        }
    }

    static String operacion(String metodo, int estado) {
        if (estado == HttpServletResponse.SC_UNAUTHORIZED || estado == HttpServletResponse.SC_FORBIDDEN) {
            return AuditoriaService.ACCESO_DENEGADO;
        }
        return HttpMethod.GET.matches(metodo) || HttpMethod.HEAD.matches(metodo)
                ? AuditoriaService.CLIENTE_CONSULTADO
                : AuditoriaService.CLIENTE_MODIFICADO;
    }

    /**
     * Identificador del cliente en la ruta, o null si la ruta es un listado o una búsqueda.
     */
    static String clienteId(String ruta) {
        Matcher coincidencia = RUTA_DE_CLIENTE.matcher(ruta);
        if (!coincidencia.matches() || coincidencia.group(1) == null) {
            return null;
        }
        String id = coincidencia.group(1);
        return id.length() > LARGO_MAXIMO_CLIENTE_ID ? id.substring(0, LARGO_MAXIMO_CLIENTE_ID) : id;
    }

    private static String usuarioActual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || autenticacion instanceof AnonymousAuthenticationToken
                || !autenticacion.isAuthenticated()) {
            return ANONIMO;
        }
        return autenticacion.getName();
    }

    /** Ruta sin el contexto de la aplicación ni los parámetros de búsqueda. */
    private static String ruta(HttpServletRequest solicitud) {
        return solicitud.getRequestURI().substring(solicitud.getContextPath().length());
    }
}
