package com.maxiconecta.crm.perfil.consentimiento;

/**
 * Estado registrado del consentimiento. Uno {@code OTORGADO} solo autoriza mientras esté dentro de
 * su vigencia (ver {@link Consentimiento#estaVigente}).
 */
public enum EstadoConsentimiento {
    OTORGADO,
    REVOCADO
}
