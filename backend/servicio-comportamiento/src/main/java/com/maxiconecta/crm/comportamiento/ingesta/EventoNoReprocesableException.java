package com.maxiconecta.crm.comportamiento.ingesta;

public class EventoNoReprocesableException extends RuntimeException {

    public EventoNoReprocesableException(String mensaje) {
        super(mensaje);
    }
}
