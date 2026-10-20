package com.maxiconecta.crm.comportamiento.comun;

/**
 * Un servicio del que depende la consulta no respondió. A diferencia de la ficha integral, que
 * degrada el bloque que falta, una comparación por segmento no puede calcularse a medias: se
 * responde 503 en lugar de un resultado en cero que parezca real.
 */
public class ServicioNoDisponibleException extends RuntimeException {

    public ServicioNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
