package com.maxiconecta.crm.fidelizacion.nivel;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Rango de puntos del periodo de evaluación que corresponde a un nivel, con ambos límites incluidos. El
 * nivel más alto no tiene máximo ({@code puntosMaximos} nulo).
 */
public record UmbralNivel(@NotNull(message = "es obligatorio") Nivel nivel,
                          @NotNull(message = "es obligatorio") @PositiveOrZero(message = "no puede ser negativo") Long puntosMinimos,
                          @PositiveOrZero(message = "no puede ser negativo") Long puntosMaximos) {

    public boolean incluye(long puntos) {
        return puntos >= puntosMinimos && (puntosMaximos == null || puntos <= puntosMaximos);
    }

    public String rango() {
        return puntosMaximos == null ? puntosMinimos + " o más" : puntosMinimos + "–" + puntosMaximos;
    }
}
