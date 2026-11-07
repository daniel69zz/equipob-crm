package com.maxiconecta.crm.fidelizacion.consulta;

import com.maxiconecta.crm.fidelizacion.puntos.SaldoPuntos;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Saldo de puntos del cliente, o {@code sinDatos=true} si todavía no tiene uno asignado (CA2 de
 * SCRUM-10: la ficha integral muestra ese bloque vacío con una leyenda, sin que sea un error).
 * <p>
 * SCRUM-49 (criterio de aceptación 3): {@code nivel} es el nivel actual (BRONCE, PLATA u ORO, o nulo si
 * no alcanza el umbral más bajo), con los puntos del periodo de evaluación con los que se asignó.
 */
public record PuntosResponse(Long saldo, String nivel, OffsetDateTime actualizadoEn, boolean sinDatos,
                             Long puntosPeriodo, OffsetDateTime nivelActualizadoEn) {

    public static PuntosResponse de(Optional<SaldoPuntos> saldo) {
        return saldo.map(s -> new PuntosResponse(s.getSaldo(), s.getNivel(), s.getActualizadoEn(), false,
                        s.getPuntosPeriodo(), s.getNivelActualizadoEn()))
                .orElseGet(() -> new PuntosResponse(null, null, null, true, null, null));
    }
}
