package com.maxiconecta.crm.interacciones.interaccion;

/** Estado de seguimiento de una interacción (SCRUM-451, docs/interacciones/seguimiento-interacciones.md). */
public enum EstadoInteraccion {
    /** Registrada y pendiente de atención. */
    ABIERTA,
    /** Un agente la está atendiendo. */
    EN_PROCESO,
    /** El caso se resolvió o no requiere más acciones: no se modifica sin reabrirla. */
    CERRADA;

    static final String PATRON = "ABIERTA|EN_PROCESO|CERRADA";
    static final String MENSAJE = "debe ser ABIERTA, EN_PROCESO o CERRADA";
}
