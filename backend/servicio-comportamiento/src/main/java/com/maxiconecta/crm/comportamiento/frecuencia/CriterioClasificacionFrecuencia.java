package com.maxiconecta.crm.comportamiento.frecuencia;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Umbrales de la clasificación por frecuencia (SCRUM-341, docs/compra/clasificacion-frecuencia.md):
 * desde {@code umbralFrecuente} compras vigentes el cliente es FRECUENTE, desde {@code umbralHabitual}
 * HABITUAL y con al menos una, OCASIONAL. Un valor exactamente en un umbral pertenece a la
 * clasificación superior. Sin compras vigentes no hay clasificación.
 */
@Component
public class CriterioClasificacionFrecuencia {

    private final long umbralHabitual;
    private final long umbralFrecuente;

    public CriterioClasificacionFrecuencia(
            @Value("${comportamiento.clasificacion-frecuencia.umbral-habitual:4}") long umbralHabitual,
            @Value("${comportamiento.clasificacion-frecuencia.umbral-frecuente:10}") long umbralFrecuente) {
        if (umbralHabitual <= 1 || umbralFrecuente <= umbralHabitual) {
            throw new IllegalStateException("Los umbrales de la clasificación por frecuencia no son válidos: el "
                    + "habitual (" + umbralHabitual + ") debe ser mayor que 1 y menor que el frecuente ("
                    + umbralFrecuente + ")");
        }
        this.umbralHabitual = umbralHabitual;
        this.umbralFrecuente = umbralFrecuente;
    }

    public long umbralHabitual() {
        return umbralHabitual;
    }

    public long umbralFrecuente() {
        return umbralFrecuente;
    }

    /** Clasificación que corresponde a una frecuencia; vacía cuando no hay compras vigentes. */
    public Optional<ClasificacionFrecuencia> clasificar(long frecuencia) {
        if (frecuencia <= 0) {
            return Optional.empty();
        }
        if (frecuencia >= umbralFrecuente) {
            return Optional.of(ClasificacionFrecuencia.FRECUENTE);
        }
        if (frecuencia >= umbralHabitual) {
            return Optional.of(ClasificacionFrecuencia.HABITUAL);
        }
        return Optional.of(ClasificacionFrecuencia.OCASIONAL);
    }
}
