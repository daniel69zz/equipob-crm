package com.maxiconecta.crm.fidelizacion.movimiento;

/**
 * Tipos de movimiento de puntos: los de RIO-CRM-03, acumulación (SCRUM-45), redención (SCRUM-48) y reversa
 * (SCRUM-524); la caducidad (SCRUM-417), que registra el propio CRM al vencer la vigencia del saldo, y el
 * ajuste manual del Administrador de CRM (SCRUM-422). Ningún evento puede traer estos dos últimos.
 */
public enum TipoMovimiento {
    ACUMULACION,
    REDENCION,
    REVERSA,
    CADUCIDAD,
    AJUSTE;

    /** Los que puede notificar Marketplace y Ventas. */
    public boolean deVentas() {
        return this != CADUCIDAD && this != AJUSTE;
    }
}
