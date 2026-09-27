package com.maxiconecta.crm.gateway.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reenvía la identidad del usuario autenticado a los microservicios mediante cabeceras
 * X-Usuario, X-Usuario-Rol y X-Usuario-Permisos. Las cabeceras con esos nombres que envíe
 * el cliente se descartan para que no puedan falsificarse.
 */
public class IdentidadHeadersFilter extends OncePerRequestFilter {

    public static final String USUARIO = "X-Usuario";
    public static final String ROL = "X-Usuario-Rol";
    public static final String PERMISOS = "X-Usuario-Permisos";

    private static final List<String> CABECERAS_DE_IDENTIDAD = List.of(USUARIO, ROL, PERMISOS);

    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        Map<String, String> identidad = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getDetails() instanceof SesionToken sesion) {
            identidad.put(USUARIO, sesion.usuario());
            identidad.put(ROL, sesion.rol());
            identidad.put(PERMISOS, String.join(",", sesion.permisos()));
        }
        cadena.doFilter(new SolicitudConIdentidad(solicitud, identidad), respuesta);
    }

    private static boolean esCabeceraDeIdentidad(String nombre) {
        return CABECERAS_DE_IDENTIDAD.stream().anyMatch(cabecera -> cabecera.equalsIgnoreCase(nombre));
    }

    private static final class SolicitudConIdentidad extends HttpServletRequestWrapper {

        private final Map<String, String> identidad;

        SolicitudConIdentidad(HttpServletRequest solicitud, Map<String, String> identidad) {
            super(solicitud);
            this.identidad = identidad;
        }

        @Override
        public String getHeader(String nombre) {
            return esCabeceraDeIdentidad(nombre) ? identidad.get(nombre) : super.getHeader(nombre);
        }

        @Override
        public Enumeration<String> getHeaders(String nombre) {
            if (!esCabeceraDeIdentidad(nombre)) {
                return super.getHeaders(nombre);
            }
            String valor = identidad.get(nombre);
            return valor == null ? Collections.emptyEnumeration() : Collections.enumeration(List.of(valor));
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> nombres = new LinkedHashSet<>();
            Enumeration<String> originales = super.getHeaderNames();
            while (originales != null && originales.hasMoreElements()) {
                String nombre = originales.nextElement();
                if (!esCabeceraDeIdentidad(nombre)) {
                    nombres.add(nombre);
                }
            }
            nombres.addAll(identidad.keySet());
            return Collections.enumeration(nombres);
        }
    }
}
