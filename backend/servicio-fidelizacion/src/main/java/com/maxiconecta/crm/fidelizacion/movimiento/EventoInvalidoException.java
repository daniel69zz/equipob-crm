package com.maxiconecta.crm.fidelizacion.movimiento;

/** El movimiento no se puede aplicar: ilegible, incompleto, inválido o de un tipo aún no soportado. */
public class EventoInvalidoException extends RuntimeException {

    public EventoInvalidoException(String mensaje) {
        super(mensaje);
    }

    public EventoInvalidoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
