package com.maxiconecta.crm.segmentacion.reporte;

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
 * Comprueba en el propio servicio el permiso de los reportes (SCRUM-462), además del control del
 * API Gateway (docs/seguridad/matriz-permisos.md). El Gateway descarta las cabeceras de identidad
 * que envía el cliente y añade las del usuario vigente; aquí se rechaza la petición sin identidad
 * (401) o cuyo rol no tiene {@code REPORTES_CONSULTAR} (403), como la del Agente de Atención al
 * Cliente (criterio de aceptación 3).
 *
 * <p>Es un permiso propio y no {@code SEGMENTOS_CONSULTAR}: el reporte cruza segmentación con
 * fidelización y es para los perfiles de análisis comercial, no para quien atiende a un cliente.
 */
@Configuration
public class PermisoReportes implements WebMvcConfigurer {

    static final String PERMISO = "REPORTES_CONSULTAR";

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(new Control()).addPathPatterns("/api/segmentacion/reportes/**");
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
