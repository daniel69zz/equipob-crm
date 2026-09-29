package com.maxiconecta.crm.comportamiento.ingesta;

/** Estado de un intento manual de reproceso. */
public enum ResultadoReproceso {
    EN_PROCESO,
    PROCESADO,
    FALLIDO,
    DESCARTADO
}
