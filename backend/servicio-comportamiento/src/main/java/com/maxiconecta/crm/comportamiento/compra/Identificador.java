package com.maxiconecta.crm.comportamiento.compra;

import com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException;

/**
 * Par (origen, idClienteOrigen) que identifica a un cliente en un canal, tal como los devuelve
 * el perfil unificado en {@code identificadoresOrigen} (GET /api/perfil/clientes/{clienteId}).
 */
public record Identificador(Origen origen, String idClienteOrigen) {

    /** Formato {@code ORIGEN:idCliente}, por ejemplo {@code MARKETPLACE:mp-user-3307}. */
    public static Identificador parsear(String texto) {
        int separador = texto == null ? -1 : texto.indexOf(':');
        if (separador <= 0 || separador == texto.length() - 1) {
            throw new ReglaNegocioException(
                    "Identificador inválido: '" + texto + "' (se espera ORIGEN:idCliente)");
        }
        String origenTexto = texto.substring(0, separador).trim().toUpperCase();
        String idCliente = texto.substring(separador + 1).trim();
        try {
            return new Identificador(Origen.valueOf(origenTexto), idCliente);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Origen desconocido en el identificador '" + texto + "': " + origenTexto);
        }
    }
}
