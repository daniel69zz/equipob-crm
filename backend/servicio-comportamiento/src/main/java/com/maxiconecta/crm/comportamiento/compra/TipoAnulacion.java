package com.maxiconecta.crm.comportamiento.compra;

/**
 * Alcance de una anulación de compra (RIO-CRM-05).
 */
public enum TipoAnulacion {
    /** La compra se anula entera: se revierte todo lo que seguía vigente. */
    TOTAL,
    /** El cliente devuelve parte de lo que compró: se revierten solo los ítems devueltos. */
    PARCIAL
}
