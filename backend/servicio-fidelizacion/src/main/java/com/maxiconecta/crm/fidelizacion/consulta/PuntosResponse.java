package com.maxiconecta.crm.fidelizacion.consulta;

import com.maxiconecta.crm.fidelizacion.puntos.SaldoPuntos;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Saldo de puntos del cliente, o {@code sinDatos=true} si todavía no tiene uno asignado (CA2 de
 * SCRUM-10: la ficha integral muestra ese bloque vacío con una leyenda, sin que sea un error).
 */
public record PuntosResponse(Integer saldo, String nivel, OffsetDateTime actualizadoEn, boolean sinDatos) {

    public static PuntosResponse de(Optional<SaldoPuntos> saldo) {
        return saldo.map(s -> new PuntosResponse(s.getSaldo(), s.getNivel(), s.getActualizadoEn(), false))
                .orElseGet(() -> new PuntosResponse(null, null, null, true));
    }
}
