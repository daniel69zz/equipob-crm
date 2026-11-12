package com.maxiconecta.crm.fidelizacion.parametro;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

/**
 * SCRUM-520: los parámetros generales solo los consulta y cambia el Administrador de CRM con el permiso
 * {@code PARAMETROS_CONFIGURAR}. El API Gateway ya lo exige; el servicio lo vuelve a comprobar con la identidad que
 * reenvía el Gateway, por si otro rol recibe el permiso por error.
 */
final class AccesoParametros {

    static final String PERMISO = "PARAMETROS_CONFIGURAR";
    static final String ROL = "ADMINISTRADOR_CRM";

    private AccesoParametros() {
    }

    /** @return el usuario; 401 sin la identidad del Gateway, 403 si no es Administrador con el permiso */
    static String administrador(HttpServletRequest solicitud) {
        String usuario = solicitud.getHeader("X-Usuario");
        if (usuario == null || usuario.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere la identidad del API Gateway");
        }
        String permisos = solicitud.getHeader("X-Usuario-Permisos");
        boolean conPermiso = permisos != null && Arrays.stream(permisos.split(",")).map(String::strip)
                .anyMatch(PERMISO::equals);
        if (!ROL.equals(solicitud.getHeader("X-Usuario-Rol")) || !conPermiso) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo el Administrador de CRM puede configurar los parámetros generales");
        }
        return usuario.strip();
    }
}
