package com.maxiconecta.crm.fidelizacion.movimiento;

/**
 * Tipos de movimiento de puntos: los de RIO-CRM-03, acumulación (SCRUM-45), redención (SCRUM-48) y reversa
 * (SCRUM-524), y la caducidad (SCRUM-417), que registra el propio CRM al vencer la vigencia del saldo y que
 * ningún evento puede traer.
 */
public enum TipoMovimiento {
    ACUMULACION,
    REDENCION,
    REVERSA,
    CADUCIDAD;

    /** Los que puede notificar Marketplace y Ventas. */
    public boolean deVentas() {
        return this != CADUCIDAD;
    }
}
