package com.maxiconecta.crm.perfil.cliente;

/**
 * De dónde proviene un dato o un cambio del cliente: del módulo Marketplace y Ventas del ERP
 * ({@code MARKETPLACE_VENTAS}), con el que el CRM tiene su única integración, o del propio CRM
 * ({@code CRM}) cuando lo origina una acción interna (vinculaciones, unificaciones, o el motor de
 * detección de SCRUM-166).
 */
public enum Origen {
    MARKETPLACE_VENTAS,
    CRM;

    /** Solo Marketplace y Ventas envía eventos y tiene identificadores de cliente. */
    public boolean esSistemaExterno() {
        return this != CRM;
    }
}
