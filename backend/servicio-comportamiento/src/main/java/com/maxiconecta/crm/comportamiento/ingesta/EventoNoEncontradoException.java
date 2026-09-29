package com.maxiconecta.crm.comportamiento.ingesta;

public class EventoNoEncontradoException extends RuntimeException {

    public EventoNoEncontradoException(Long idEvento) {
        super("No existe el evento " + idEvento + " en la bitácora");
    }
}
