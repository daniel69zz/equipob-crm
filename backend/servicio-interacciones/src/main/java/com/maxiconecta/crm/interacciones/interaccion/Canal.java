package com.maxiconecta.crm.interacciones.interaccion;

/** Por dónde llegó la interacción (opcional). */
public enum Canal {
    TELEFONO, CORREO, PRESENCIAL, CHAT, WHATSAPP, OTRO;

    /** Vacío equivale a no indicarlo. */
    static final String PATRON = "\\s*|TELEFONO|CORREO|PRESENCIAL|CHAT|WHATSAPP|OTRO";
    static final String MENSAJE = "debe ser TELEFONO, CORREO, PRESENCIAL, CHAT, WHATSAPP u OTRO";
}
