package com.maxiconecta.crm.perfil.cliente;

/**
 * Tipo de operación registrada en el histórico del perfil.
 */
public enum TipoCambio {
    /** Un alta de Marketplace o Ventas creó el perfil. */
    CREACION,
    /** Una notificación de Marketplace o Ventas modificó el perfil. */
    ACTUALIZACION,
    /** Un administrador vinculó un identificador de origen al perfil. */
    VINCULACION,
    /** Un administrador unificó dos perfiles duplicados. */
    UNIFICACION
}
