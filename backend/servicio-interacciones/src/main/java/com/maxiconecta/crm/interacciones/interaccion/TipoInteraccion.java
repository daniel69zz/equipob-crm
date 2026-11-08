package com.maxiconecta.crm.interacciones.interaccion;

/** Qué se registra de la atención al cliente (SCRUM-433). */
public enum TipoInteraccion {
    /** El cliente pide información. */
    CONSULTA,
    /** El cliente expresa una queja o un problema. */
    RECLAMO,
    /** Observación interna del agente sobre la atención. */
    NOTA;

    static final String PATRON = "CONSULTA|RECLAMO|NOTA";
    static final String MENSAJE = "debe ser CONSULTA, RECLAMO o NOTA";
}
