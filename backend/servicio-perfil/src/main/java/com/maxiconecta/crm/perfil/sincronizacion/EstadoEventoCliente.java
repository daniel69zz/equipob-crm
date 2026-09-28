package com.maxiconecta.crm.perfil.sincronizacion;

/**
 * Estado de un mensaje en la bitácora de sincronización del perfil.
 */
public enum EstadoEventoCliente {
    /** Recibido y todavía sin procesar. */
    RECIBIDO,
    /** El perfil quedó creado o actualizado y completo. */
    PROCESADO,
    /** El perfil quedó creado o actualizado, pero con datos obligatorios vacíos o mal formados. */
    INCOMPLETO,
    /** No se aplicó: evento más antiguo que el último aplicado o reentrega del mismo cambio. */
    DESCARTADO,
    /** No se pudo procesar; queda disponible para reproceso. */
    FALLIDO
}
