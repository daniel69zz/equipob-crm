package com.maxiconecta.crm.comportamiento.reporte;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Comprueba en el propio servicio el permiso del reporte de comportamiento de compra (SCRUM-58),
 * además del control del API Gateway, igual que los reportes de servicio-segmentacion (SCRUM-462).
 * Sin la identidad que añade el Gateway responde 401, y sin {@code REPORTES_CONSULTAR} 403: el Agente
 * de Atención consulta los indicadores de un cliente, pero no los reportes de la cartera.
 */
@Configuration
public class PermisoReportes implements WebMvcConfigurer {

    static final String PERMISO = "REPORTES_CONSULTAR";

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        // SCRUM-500: también el comparativo (SCRUM-62) y su exportación. No las consultas internas del panel.
        registro.addInterceptor(new Control()).addPathPatterns("/api/comportamiento/reportes/comportamiento",
                "/api/comportamiento/reportes/comparativo", "/api/comportamiento/reportes/comparativo/**");
    }

    static final class Control implements HandlerInterceptor {

        @Override
        public boolean preHandle(HttpServletRequest solicitud, HttpServletResponse respuesta, Object controlador) {
            String usuario = solicitud.getHeader("X-Usuario");
            String permisos = solicitud.getHeader("X-Usuario-Permisos");
            if (usuario == null || usuario.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere la identidad del Gateway");
            }
            if (permisos == null || Arrays.stream(permisos.split(",")).map(String::strip).noneMatch(PERMISO::equals)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Su rol no tiene permiso para consultar los reportes de clientes");
            }
            return true;
        }
    }
}
