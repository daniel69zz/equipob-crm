package com.maxiconecta.crm.comportamiento.ingesta;

/**
 * La transacción del evento ya fue procesada (misma clave de idempotencia). El mensaje queda
 * DESCARTADO sin efectos. Ver docs/ingesta/idempotencia-eventos-venta.md.
 */
public class EventoDuplicadoException extends RuntimeException {

    public EventoDuplicadoException(String mensaje) {
        super(mensaje);
    }
}
