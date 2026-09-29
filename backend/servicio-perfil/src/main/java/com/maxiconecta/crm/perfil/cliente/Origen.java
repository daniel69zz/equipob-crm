package com.maxiconecta.crm.perfil.cliente;

/**
 * Sistema del que proviene un dato o un cambio del cliente: Marketplace, Ventas, o el propio CRM
 * cuando lo hace un administrador (vinculaciones, unificaciones).
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
