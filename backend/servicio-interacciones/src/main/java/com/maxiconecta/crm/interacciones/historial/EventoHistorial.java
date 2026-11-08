package com.maxiconecta.crm.interacciones.historial;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.maxiconecta.crm.interacciones.compras.Compra;
import com.maxiconecta.crm.interacciones.interaccion.Interaccion;

import java.time.OffsetDateTime;
import java.util.Comparator;

/** Un evento de la línea de tiempo del cliente (SCRUM-53): una interacción o una compra, con su fecha. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventoHistorial(Tipo tipo, OffsetDateTime fecha, Interaccion interaccion, Compra compra) {

    /** En este orden se desempata una misma fecha: la compra suele originar la interacción (SCRUM-441). */
    public enum Tipo { COMPRA, INTERACCION }

    /** Del más reciente al más antiguo; a igual fecha, la compra primero. */
    static final Comparator<EventoHistorial> CRONOLOGICO = Comparator
            .comparing(EventoHistorial::fecha, Comparator.nullsLast(Comparator.<OffsetDateTime>reverseOrder()))
            .thenComparing(EventoHistorial::tipo);

    static EventoHistorial de(Interaccion interaccion) {
        return new EventoHistorial(Tipo.INTERACCION, interaccion.registradoEn(), interaccion, null);
    }

    static EventoHistorial de(Compra compra) {
        return new EventoHistorial(Tipo.COMPRA, compra.fecha(), null, compra);
    }
}
