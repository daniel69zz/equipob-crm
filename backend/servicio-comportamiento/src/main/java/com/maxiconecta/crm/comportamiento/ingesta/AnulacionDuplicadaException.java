package com.maxiconecta.crm.comportamiento.ingesta;

/**
 * La anulación o devolución del evento ya estaba registrada.
 */
public class AnulacionDuplicadaException extends EventoDuplicadoException {

    public AnulacionDuplicadaException(String mensaje) {
        super(mensaje);
    }
}
