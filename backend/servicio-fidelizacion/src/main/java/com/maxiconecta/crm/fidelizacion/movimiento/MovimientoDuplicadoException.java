package com.maxiconecta.crm.fidelizacion.movimiento;

/** La transacción ya acreditó puntos (SCRUM-243): el evento se descarta sin tocar el saldo. */
public class MovimientoDuplicadoException extends RuntimeException {

    public MovimientoDuplicadoException(String mensaje) {
        super(mensaje);
    }
}
