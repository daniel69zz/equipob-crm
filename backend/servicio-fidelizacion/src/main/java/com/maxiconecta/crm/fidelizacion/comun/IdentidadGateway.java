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
    public static final String AGENTE_ATENCION = "AGENTE_ATENCION";
    public static final String FIDELIZACION_CONFIGURAR = "FIDELIZACION_CONFIGURAR";
    public static final String PUNTOS_CONSULTAR = "PUNTOS_CONSULTAR";
    /** SCRUM-545: rol y permiso de un cliente de servicio del ERP (token de servicio del Gateway). */
    public static final String SISTEMA = "SISTEMA";
    public static final String PUNTOS_CONSULTAR_VENTAS = "PUNTOS_CONSULTAR_VENTAS";

    private IdentidadGateway() {
    }

    /** @return el usuario administrador; si no lo es, 401 (sin identidad) o 403 (otro rol o sin permiso) */
    public static String administradorFidelizacion(HttpServletRequest solicitud, String accion) {
        String usuario = usuario(solicitud);
        if (!ADMINISTRADOR_CRM.equals(solicitud.getHeader(ROL)) || !tienePermiso(solicitud, FIDELIZACION_CONFIGURAR)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el Administrador de CRM puede " + accion);
        }
        return usuario;
    }

    /**
     * SCRUM-396: la consulta de puntos de un cliente exige la identidad del Gateway y el permiso
     * {@code PUNTOS_CONSULTAR}.
     *
     * @return el usuario y su rol; 401 sin identidad, 403 sin el permiso
     */
    public static Identidad consultaPuntos(HttpServletRequest solicitud) {
        String usuario = usuario(solicitud);
        if (!tienePermiso(solicitud, PUNTOS_CONSULTAR)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permiso para consultar los puntos de los clientes");
        }
        String rol = solicitud.getHeader(ROL);
        return new Identidad(usuario, rol == null ? null : rol.strip());
    }

    /**
     * SCRUM-545 (RIO-CRM-04): la consulta de puntos de Marketplace y Ventas exige la identidad de un cliente de
     * servicio con el permiso {@code PUNTOS_CONSULTAR_VENTAS}. Un usuario interno no la usa, aunque tenga
     * {@code PUNTOS_CONSULTAR}.
     *
     * @return el cliente de servicio; 401 sin identidad, 403 si no es un sistema con el permiso
     */
    public static String sistemaVentas(HttpServletRequest solicitud) {
        String cliente = usuario(solicitud);
        if (!SISTEMA.equals(solicitud.getHeader(ROL)) || !tienePermiso(solicitud, PUNTOS_CONSULTAR_VENTAS)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Esta consulta es solo para Marketplace y Ventas, con su token de servicio");
        }
        return cliente;
    }

    private static String usuario(HttpServletRequest solicitud) {
        String usuario = solicitud.getHeader(USUARIO);
        if (usuario == null || usuario.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere la identidad del API Gateway");
        }
        return usuario.strip();
    }

    private static boolean tienePermiso(HttpServletRequest solicitud, String permiso) {
        String permisos = solicitud.getHeader(PERMISOS);
        return permisos != null && Arrays.stream(permisos.split(",")).map(String::trim).anyMatch(permiso::equals);
    }

    /** Usuario autenticado en el Gateway y su rol. */
    public record Identidad(String usuario, String rol) {
    }
}
