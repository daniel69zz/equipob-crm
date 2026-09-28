package com.maxiconecta.crm.comportamiento.ingesta;

/**
 * La compra del evento ya estaba registrada.
 */
public class CompraDuplicadaException extends RuntimeException {

    public CompraDuplicadaException(String mensaje) {
        super(mensaje);
    }
}
