package com.maxiconecta.crm.fidelizacion.comun;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

/**
 * Identidad que el API Gateway reenvía en las cabeceras {@code X-Usuario}, {@code X-Usuario-Rol} y
 * {@code X-Usuario-Permisos} (las que envíe el cliente se descartan en el Gateway). El puerto del servicio
 * debe quedar en la red interna, como en los demás microservicios.
 * <p>
 * La configuración del programa de fidelización exige el rol de Administrador de CRM además del permiso
 * {@code FIDELIZACION_CONFIGURAR}, aunque otro rol reciba por error ese permiso (igual que la
 * segmentación).
 */
public final class IdentidadGateway {

    public static final String USUARIO = "X-Usuario";
    public static final String ROL = "X-Usuario-Rol";
    public static final String PERMISOS = "X-Usuario-Permisos";
    public static final String ADMINISTRADOR_CRM = "ADMINISTRADOR_CRM";
    public static final String FIDELIZACION_CONFIGURAR = "FIDELIZACION_CONFIGURAR";

    private IdentidadGateway() {
    }

    /** @return el usuario administrador; si no lo es, 401 (sin identidad) o 403 (otro rol o sin permiso) */
    public static String administradorFidelizacion(HttpServletRequest solicitud, String accion) {
        String usuario = solicitud.getHeader(USUARIO);
        if (usuario == null || usuario.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere la identidad del API Gateway");
        }
        String permisos = solicitud.getHeader(PERMISOS);
        if (!ADMINISTRADOR_CRM.equals(solicitud.getHeader(ROL)) || permisos == null
                || Arrays.stream(permisos.split(",")).map(String::trim).noneMatch(FIDELIZACION_CONFIGURAR::equals)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el Administrador de CRM puede " + accion);
        }
        return usuario.strip();
    }
}
