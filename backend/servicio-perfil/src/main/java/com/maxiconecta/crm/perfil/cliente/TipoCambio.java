package com.maxiconecta.crm.perfil.cliente;

/**
 * Tipo de operación registrada en el histórico del perfil.
 */
public enum TipoCambio {
    /** Un alta del módulo Marketplace y Ventas creó el perfil. */
    CREACION,
    /** Una notificación del módulo Marketplace y Ventas modificó el perfil. */
    ACTUALIZACION,
    /** Un administrador vinculó un identificador de origen al perfil. */
    VINCULACION,
    /** Un administrador unificó dos perfiles duplicados. */
    UNIFICACION,
    /** El motor de detección (SCRUM-166) etiquetó el perfil al reevaluarlo. */
    DETECCION
}
