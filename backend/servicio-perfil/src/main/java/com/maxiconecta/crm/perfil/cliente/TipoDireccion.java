package com.maxiconecta.crm.perfil.cliente;

/**
 * Uso de una dirección del cliente.
 */
public enum TipoDireccion {
    ENTREGA,
    FACTURACION,
    OTRA;

    /** Tipo informado por el sistema de origen; si no viene o no se reconoce, OTRA. */
    public static TipoDireccion de(String valor) {
        if (valor == null) {
            return OTRA;
        }
        try {
            return valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return OTRA;
        }
    }
}
