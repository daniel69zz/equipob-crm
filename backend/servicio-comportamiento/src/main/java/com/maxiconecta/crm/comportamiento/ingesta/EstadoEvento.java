package com.maxiconecta.crm.comportamiento.ingesta;

/**
 * Estado de un mensaje en la bitácora de ingesta.
 */
public enum EstadoEvento {
    /** Recibido y todavía sin procesar. */
    RECIBIDO,
    /** La compra quedó registrada. */
    PROCESADO,
    /** No se pudo procesar; queda disponible para reproceso. */
    FALLIDO,
    /** Espera de revisión manual; se representa mediante un intento pendiente, sin procesar la compra. */
    EN_ESPERA,
    /** No se registró porque la compra ya existía. */
    DESCARTADO
}
