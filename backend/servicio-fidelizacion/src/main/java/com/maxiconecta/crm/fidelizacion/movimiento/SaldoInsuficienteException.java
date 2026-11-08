package com.maxiconecta.crm.fidelizacion.movimiento;

/**
 * La redención pide más puntos que el saldo disponible del cliente (SCRUM-407, criterio de aceptación 2
 * de SCRUM-48). El movimiento queda FALLIDO con la causa y el saldo no cambia; se puede reprocesar si
 * el cliente acumula después los puntos que faltaban.
 */
public class SaldoInsuficienteException extends RuntimeException {

    private final long solicitados;
    private final long disponibles;

    public SaldoInsuficienteException(String idTransaccion, long solicitados, long disponibles) {
        this(idTransaccion, solicitados, disponibles, "");
    }

    /** @param detalle se agrega al final de la causa, por ejemplo los puntos que caducaron (SCRUM-419) */
    public SaldoInsuficienteException(String idTransaccion, long solicitados, long disponibles, String detalle) {
        super("Saldo insuficiente para la redención " + idTransaccion + ": se solicitan " + solicitados
                + " puntos y el saldo disponible es " + disponibles + detalle);
        this.solicitados = solicitados;
        this.disponibles = disponibles;
    }

    public long solicitados() {
        return solicitados;
    }

    public long disponibles() {
        return disponibles;
    }
}
