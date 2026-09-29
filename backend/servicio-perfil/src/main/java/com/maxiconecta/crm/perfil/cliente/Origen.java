package com.maxiconecta.crm.perfil.cliente;

/**
 * Sistema del que proviene un cambio del perfil: Marketplace o Ventas cuando lo origina un evento
 * externo, o el propio CRM ({@code SISTEMA}) cuando lo origina el motor de detección (SCRUM-166).
 */
public enum Origen {
    MARKETPLACE,
    VENTAS,
    SISTEMA
}
