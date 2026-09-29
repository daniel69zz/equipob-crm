package com.maxiconecta.crm.perfil.consentimiento;

/**
 * Medio por el que el cliente otorgó su consentimiento: a través del módulo Marketplace y Ventas o
 * directamente ante el CRM (en persona, por teléfono o por correo).
 */
public enum CanalConsentimiento {
    MARKETPLACE_VENTAS,
    PRESENCIAL,
    TELEFONICO,
    CORREO
}
