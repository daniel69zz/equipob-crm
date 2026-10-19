package com.maxiconecta.crm.comportamiento.frecuencia;

/**
 * Clasificación por frecuencia de un cliente con la frecuencia que la origina (SCRUM-38).
 * {@code sinClasificacion} avisa que el cliente no tiene compras vigentes; en ese caso
 * {@code clasificacion} es {@code null} y no hay error (criterio de aceptación 3).
 */
public record ClasificacionFrecuenciaCliente(ClasificacionFrecuencia clasificacion, long frecuencia,
                                             boolean sinClasificacion) {

    public static ClasificacionFrecuenciaCliente de(long frecuencia, CriterioClasificacionFrecuencia criterio) {
        return criterio.clasificar(frecuencia)
                .map(clasificacion -> new ClasificacionFrecuenciaCliente(clasificacion, frecuencia, false))
                .orElseGet(() -> new ClasificacionFrecuenciaCliente(null, 0, true));
    }
}
