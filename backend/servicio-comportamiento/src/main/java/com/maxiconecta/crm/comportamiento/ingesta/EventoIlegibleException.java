package com.maxiconecta.crm.comportamiento.ingesta;

/**
 * El mensaje no se puede interpretar como un evento de compra confirmada.
 */
public class EventoIlegibleException extends RuntimeException {

    public EventoIlegibleException(String mensaje) {
        super(mensaje);
    }

    public EventoIlegibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
