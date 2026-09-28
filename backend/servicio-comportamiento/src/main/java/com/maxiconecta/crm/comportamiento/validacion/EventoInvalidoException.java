package com.maxiconecta.crm.comportamiento.validacion;

/**
 * El evento no cumple una regla de validación. La causa queda en la bitácora de ingesta.
 */
public class EventoInvalidoException extends RuntimeException {

    public EventoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
