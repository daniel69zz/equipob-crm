package com.maxiconecta.crm.perfil.sincronizacion;

/**
 * El mensaje no se puede interpretar como un evento de datos del cliente: no se sabe a qué
 * cliente ni a qué sistema corresponde. El mensaje queda FALLIDO.
 */
public class EventoIlegibleException extends RuntimeException {

    public EventoIlegibleException(String mensaje) {
        super(mensaje);
    }

    public EventoIlegibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
