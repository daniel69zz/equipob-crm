package com.maxiconecta.crm.interacciones.comun;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Set;

/**
 * Identidad que el API Gateway reenvía en las cabeceras {@code X-Usuario}, {@code X-Usuario-Rol} y
 * {@code X-Usuario-Permisos} (las que envíe el navegador se descartan en el Gateway). El puerto del servicio queda
 * en la red interna, como en los demás microservicios. Permisos de SCRUM-435
 * (docs/interacciones/registro-interacciones.md).
 */
public final class IdentidadGateway {

    public static final String USUARIO = "X-Usuario";
    public static final String ROL = "X-Usuario-Rol";
    public static final String PERMISOS = "X-Usuario-Permisos";
    public static final String ADMINISTRADOR_CRM = "ADMINISTRADOR_CRM";
    public static final String AGENTE_ATENCION = "AGENTE_ATENCION";
    public static final String INTERACCIONES_CONSULTAR = "INTERACCIONES_CONSULTAR";
    public static final String INTERACCIONES_REGISTRAR = "INTERACCIONES_REGISTRAR";
    public static final String AUDITORIA_CONSULTAR = "AUDITORIA_CONSULTAR";
    public static final String INDICADORES_CONSULTAR = "INDICADORES_CONSULTAR";

    private static final Set<String> ROLES_QUE_REGISTRAN = Set.of(ADMINISTRADOR_CRM, AGENTE_ATENCION);
    /** Roles de atención que consultan el historial del cliente con sus compras (SCRUM-438). */
    private static final Set<String> ROLES_DE_ATENCION = Set.of(ADMINISTRADOR_CRM, AGENTE_ATENCION);

    private IdentidadGateway() {
    }

    /** @return el usuario; 401 sin identidad y 403 sin {@code INTERACCIONES_CONSULTAR} */
    public static Identidad consulta(HttpServletRequest solicitud) {
        Identidad identidad = identidad(solicitud);
        if (!tienePermiso(solicitud, INTERACCIONES_CONSULTAR)) {
            throw prohibido("No tiene permiso para consultar las interacciones de los clientes");
        }
        return identidad;
    }

    /**
     * Registrar o modificar exige {@code INTERACCIONES_REGISTRAR} y el rol de Administrador de CRM o de Agente de
     * Atención, aunque otro rol reciba el permiso por error.
     */
    public static Identidad registro(HttpServletRequest solicitud) {
        Identidad identidad = identidad(solicitud);
        if (!ROLES_QUE_REGISTRAN.contains(identidad.rol()) || !tienePermiso(solicitud, INTERACCIONES_REGISTRAR)) {
            throw prohibido("Solo el Agente de Atención o el Administrador de CRM pueden registrar interacciones");
        }
        return identidad;
    }

    /**
     * El historial del cliente con sus compras (SCRUM-53, criterio de aceptación 3): el Administrador de CRM o el
     * Agente de Atención, con {@code INTERACCIONES_CONSULTAR} y con {@code INDICADORES_CONSULTAR}, que es el que pide
     * el historial de compras.
     */
    public static Identidad historialCliente(HttpServletRequest solicitud) {
        Identidad identidad = identidad(solicitud);
        if (!ROLES_DE_ATENCION.contains(identidad.rol()) || !tienePermiso(solicitud, INTERACCIONES_CONSULTAR)
                || !tienePermiso(solicitud, INDICADORES_CONSULTAR)) {
            throw prohibido("Solo el Agente de Atención o el Administrador de CRM consultan el historial del cliente "
                    + "con sus compras");
        }
        return identidad;
    }

    /**
     * Vincular o desvincular una compra (SCRUM-447): el Agente de Atención o el Administrador de CRM, con
     * {@code INTERACCIONES_REGISTRAR} y con {@code INDICADORES_CONSULTAR}, que da acceso al historial de compras.
     */
    public static Identidad vinculacionCompra(HttpServletRequest solicitud) {
        Identidad identidad = registro(solicitud);
        if (!tienePermiso(solicitud, INDICADORES_CONSULTAR)) {
            throw prohibido("No tiene permiso para consultar las compras del cliente y vincularlas a una interacción");
        }
        return identidad;
    }

    /**
     * Asignar o reasignar el responsable de una interacción, y listar los agentes para elegirlo (SCRUM-456): solo el
     * Administrador de CRM con {@code INTERACCIONES_REGISTRAR}. El Agente de Atención ve el responsable, pero no lo
     * cambia (criterio de aceptación 3 de SCRUM-56).
     */
    public static Identidad asignacion(HttpServletRequest solicitud) {
        Identidad identidad = identidad(solicitud);
        if (!identidad.esAdministrador() || !tienePermiso(solicitud, INTERACCIONES_REGISTRAR)) {
            throw prohibido("Solo el Administrador de CRM puede asignar interacciones");
        }
        return identidad;
    }

    /** La auditoría de una interacción, como la pantalla de auditoría: Administrador con {@code AUDITORIA_CONSULTAR}. */
    public static Identidad auditoria(HttpServletRequest solicitud) {
        Identidad identidad = identidad(solicitud);
        if (!identidad.esAdministrador() || !tienePermiso(solicitud, AUDITORIA_CONSULTAR)) {
            throw prohibido("Solo el Administrador de CRM puede consultar la auditoría de las interacciones");
        }
        return identidad;
    }

    public static ResponseStatusException prohibido(String mensaje) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, mensaje);
    }

    private static Identidad identidad(HttpServletRequest solicitud) {
        String usuario = solicitud.getHeader(USUARIO);
        if (usuario == null || usuario.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere la identidad del API Gateway");
        }
        String rol = solicitud.getHeader(ROL);
        return new Identidad(usuario.strip(), rol == null || rol.isBlank() ? null : rol.strip());
    }

    private static boolean tienePermiso(HttpServletRequest solicitud, String permiso) {
        String permisos = solicitud.getHeader(PERMISOS);
        return permisos != null && Arrays.stream(permisos.split(",")).map(String::trim).anyMatch(permiso::equals);
    }

    /** Usuario autenticado en el Gateway y su rol. */
    public record Identidad(String usuario, String rol) {

        public boolean esAdministrador() {
            return ADMINISTRADOR_CRM.equals(rol);
        }
    }
}
