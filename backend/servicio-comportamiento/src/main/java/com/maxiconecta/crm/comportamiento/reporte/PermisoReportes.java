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
import java.util.List;

/**
 * Comprueba en el propio servicio el permiso del reporte de comportamiento de compra (SCRUM-58),
 * además del control del API Gateway, igual que los reportes de servicio-segmentacion (SCRUM-462).
 * Sin la identidad que añade el Gateway responde 401, y sin {@code REPORTES_CONSULTAR} 403: el Agente
 * de Atención consulta los indicadores de un cliente, pero no los reportes de la cartera.
 *
 * <p>Exportar un reporte a CSV o PDF (SCRUM-60) exige además {@code REPORTES_EXPORTAR} (SCRUM-482): un rol que
 * consulta los reportes pero no puede exportarlos recibe 403 (criterio de aceptación 2).
 *
 * <p>Los reportes programados (SCRUM-61) generan y entregan archivos exportados, así que exigen los tres permisos:
 * consultar, exportar y {@code REPORTES_PROGRAMAR}.
 */
@Configuration
public class PermisoReportes implements WebMvcConfigurer {

    static final String PERMISO = "REPORTES_CONSULTAR";
    static final String PERMISO_EXPORTAR = "REPORTES_EXPORTAR";
    static final String PERMISO_PROGRAMAR = "REPORTES_PROGRAMAR";

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        // SCRUM-61: un interceptor por permiso, para que falte el que falte la respuesta sea 403.
        for (String permiso : List.of(PERMISO, PERMISO_EXPORTAR, PERMISO_PROGRAMAR)) {
            registro.addInterceptor(new Control(permiso, "Su rol no tiene permiso para programar reportes"))
                    .addPathPatterns("/api/comportamiento/reportes/programaciones",
                            "/api/comportamiento/reportes/programaciones/**");
        }
        // SCRUM-500: también el comparativo (SCRUM-62) y su exportación. No las consultas internas del panel.
        registro.addInterceptor(new Control(PERMISO, "Su rol no tiene permiso para consultar los reportes de clientes"))
                .addPathPatterns("/api/comportamiento/reportes/comportamiento", "/api/comportamiento/reportes/comportamiento/**",
                        "/api/comportamiento/reportes/comparativo", "/api/comportamiento/reportes/comparativo/**");
        // SCRUM-482: la exportación a CSV y PDF (SCRUM-60), incluida la ruta del CSV de SCRUM-499.
        registro.addInterceptor(new Control(PERMISO_EXPORTAR, "Su rol no tiene permiso para exportar los reportes de clientes"))
                .addPathPatterns("/api/comportamiento/reportes/*/exportar", "/api/comportamiento/reportes/comparativo/csv");
    }

    static final class Control implements HandlerInterceptor {

        private final String permiso;
        private final String motivo;

        Control(String permiso, String motivo) {
            this.permiso = permiso;
            this.motivo = motivo;
        }

        @Override
        public boolean preHandle(HttpServletRequest solicitud, HttpServletResponse respuesta, Object controlador) {
            String usuario = solicitud.getHeader("X-Usuario");
            String permisos = solicitud.getHeader("X-Usuario-Permisos");
            if (usuario == null || usuario.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere la identidad del Gateway");
            }
            if (permisos == null || Arrays.stream(permisos.split(",")).map(String::strip).noneMatch(permiso::equals)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, motivo);
            }
            return true;
        }
    }
}
