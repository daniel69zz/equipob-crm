package com.maxiconecta.crm.interacciones.historial;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.maxiconecta.crm.interacciones.interaccion.Interaccion;

import java.time.OffsetDateTime;

/** Un evento de la línea de tiempo del cliente (SCRUM-53): una interacción, con su fecha. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventoHistorial(Tipo tipo, OffsetDateTime fecha, Interaccion interaccion) {

    public enum Tipo { INTERACCION }

    static EventoHistorial de(Interaccion interaccion) {
        return new EventoHistorial(Tipo.INTERACCION, interaccion.registradoEn(), interaccion);
    }
}
