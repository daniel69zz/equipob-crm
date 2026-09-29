package com.maxiconecta.crm.perfil.consentimiento;

/**
 * Tipo de cambio registrado en el historial del consentimiento.
 */
public enum OperacionConsentimiento {
    /** El cliente otorga su consentimiento por primera vez o después de haberlo revocado. */
    OTORGAMIENTO,
    /** Cambia el canal, el alcance o la vigencia de un consentimiento otorgado. */
    ACTUALIZACION,
    /** El cliente retira su consentimiento. */
    REVOCACION
}
