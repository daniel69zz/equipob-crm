package com.maxiconecta.crm.perfil.configuracion;

import com.maxiconecta.crm.perfil.cliente.Origen;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Regla de prioridad para los datos de identificación cuando dos sistemas informan valores
 * distintos (docs/perfil/mapeo-datos-perfil.md). El primero de la lista gana.
 */
@ConfigurationProperties(prefix = "crm.perfil.conflictos")
public record PoliticaConflictos(List<Origen> prioridadIdentificacion) {

    public PoliticaConflictos {
        prioridadIdentificacion = prioridadIdentificacion == null || prioridadIdentificacion.isEmpty()
                ? List.of(Origen.VENTAS, Origen.MARKETPLACE)
                : List.copyOf(prioridadIdentificacion);
    }

    /** Menor es más prioritario; un sistema que no está en la lista va al final. */
    public int prioridad(Origen origen) {
        int posicion = prioridadIdentificacion.indexOf(origen);
        return posicion < 0 ? Integer.MAX_VALUE : posicion;
    }
}
