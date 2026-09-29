package com.maxiconecta.crm.perfil.cliente;

/**
 * Sistema del que proviene un dato o un cambio del cliente: Marketplace o Ventas cuando lo
 * origina un evento externo, o el propio CRM ({@code CRM}) cuando lo origina una acción interna
 * (vinculaciones, unificaciones, o el motor de detección de SCRUM-166).
 */
public enum Origen {
    MARKETPLACE,
    VENTAS,
    CRM;

    /** Solo Marketplace y Ventas envían eventos y tienen identificadores de cliente. */
    public boolean esSistemaExterno() {
        return this != CRM;
    }
}
