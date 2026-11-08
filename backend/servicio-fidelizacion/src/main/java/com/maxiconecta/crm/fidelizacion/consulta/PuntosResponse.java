package com.maxiconecta.crm.fidelizacion.consulta;

import com.maxiconecta.crm.fidelizacion.caducidad.SaldoVigente;
import com.maxiconecta.crm.fidelizacion.puntos.SaldoPuntos;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Saldo de puntos del cliente, o {@code sinDatos=true} si todavía no tiene uno asignado (CA2 de
 * SCRUM-10: la ficha integral muestra ese bloque vacío con una leyenda, sin que sea un error).
 * <p>
 * SCRUM-49 (criterio de aceptación 3): {@code nivel} es el nivel actual (BRONCE, PLATA u ORO, o nulo si
 * no alcanza el umbral más bajo), con los puntos del periodo de evaluación con los que se asignó.
 * <p>
 * SCRUM-419: {@code saldo} es el disponible. Si la vigencia ya venció y el proceso de caducidad aún no lo
 * descontó, es 0 y lo vencido va en {@code puntosCaducados}.
 */
public record PuntosResponse(Long saldo, String nivel, OffsetDateTime actualizadoEn, boolean sinDatos,
                             Long puntosPeriodo, OffsetDateTime nivelActualizadoEn, OffsetDateTime fechaCaducidad,
                             Long puntosCaducados) {

    public static PuntosResponse de(Optional<SaldoPuntos> saldo, SaldoVigente vigente) {
        return saldo.map(s -> {
                    SaldoVigente.Estado estado = vigente.evaluar(s.getClienteId(), s.getSaldo(),
                            OffsetDateTime.now(ZoneOffset.UTC));
                    return new PuntosResponse(estado.saldoDisponible(), s.getNivel(), s.getActualizadoEn(), false,
                            s.getPuntosPeriodo(), s.getNivelActualizadoEn(), estado.fechaCaducidad(),
                            estado.puntosCaducados());
                })
                .orElseGet(() -> new PuntosResponse(null, null, null, true, null, null, null, null));
    }
}
