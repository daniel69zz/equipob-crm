package com.maxiconecta.crm.perfil.sincronizacion;

/**
 * El evento no se aplica al perfil (por ejemplo, es más antiguo que el último aplicado).
 * El mensaje queda DESCARTADO con este motivo.
 */
public class EventoDescartadoException extends RuntimeException {

    private final Long idCliente;

    public EventoDescartadoException(Long idCliente, String mensaje) {
        super(mensaje);
        this.idCliente = idCliente;
    }

    public Long getIdCliente() {
        return idCliente;
    }
}
