package com.maxiconecta.crm.gateway.seguridad;

import java.util.List;

/**
 * Datos del usuario contenidos en un token JWT válido.
 */
public record SesionToken(String usuario, String nombreCompleto, String rol, List<String> permisos) {
}
