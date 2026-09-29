package com.maxiconecta.crm.comportamiento.compra;

import com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException;

import java.util.List;

/**
 * Identificador del cliente en el módulo Marketplace y Ventas, tal como lo devuelve el perfil
 * unificado en {@code identificadoresOrigen} (GET /api/perfil/clientes/{clienteId}). Un cliente
 * puede tener varios si se unificaron perfiles duplicados.
 */
public record Identificador(String idClienteOrigen) {

    static final int LARGO_MAXIMO = 64;

    /** Parsea los parámetros repetibles de una consulta; sin parámetros, la lista viene vacía. */
    public static List<Identificador> parsearTodos(List<String> textos) {
        return (textos == null ? List.<String>of() : textos).stream().map(Identificador::parsear).toList();
    }

    public static Identificador parsear(String texto) {
        String idCliente = texto == null ? "" : texto.trim();
        if (idCliente.isEmpty() || idCliente.length() > LARGO_MAXIMO) {
            throw new ReglaNegocioException("Identificador inválido: '" + texto
                    + "' (se espera el identificador del cliente en Marketplace y Ventas, de hasta "
                    + LARGO_MAXIMO + " caracteres)");
        }
        return new Identificador(idCliente);
    }
}
