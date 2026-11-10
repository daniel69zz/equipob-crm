package com.maxiconecta.crm.interacciones.notificacion;

import java.time.OffsetDateTime;

/**
 * Aviso a un usuario del CRM (SCRUM-459): por ahora, que se le asignó una interacción. {@code leidaEn} es nulo
 * mientras no la marque como leída.
 */
public record Notificacion(long id, String tipo, long interaccionId, long clienteId, String mensaje,
                           String comentario, String creadaPor, OffsetDateTime creadaEn, OffsetDateTime leidaEn) {

    /** Se le asignó una interacción al destinatario. */
    public static final String INTERACCION_ASIGNADA = "INTERACCION_ASIGNADA";
}
