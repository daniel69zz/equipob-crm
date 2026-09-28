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
    /** No se registró porque la compra ya existía. */
    DESCARTADO
}
