package com.maxiconecta.crm.perfil.consentimiento;

/**
 * Finalidad para la que el cliente autoriza el uso de sus datos (docs/perfil/consentimiento-datos.md).
 */
public enum AlcanceConsentimiento {
    /** Mantener el perfil y atender al cliente. */
    GESTION_CLIENTE,
    /** Calcular indicadores de compra: ticket, frecuencia, recencia y valor. */
    ANALISIS_COMPORTAMIENTO,
    /** Asignar al cliente a segmentos. */
    SEGMENTACION,
    /** Acumular y canjear puntos. */
    FIDELIZACION,
    /** Enviarle ofertas y campañas. */
    COMUNICACIONES_COMERCIALES
}
