package com.maxiconecta.crm.comportamiento.parametro;

import java.util.Map;

/** Otro usuario cambió el parámetro después de que se cargó la pantalla: 409 (SCRUM-65). */
public class ParametroDesactualizadoException extends RuntimeException {

    private final Map<String, String> campos;

    public ParametroDesactualizadoException(Map<String, String> campos) {
        super("Otro usuario cambió " + String.join(", ", campos.keySet())
                + " mientras tanto. Recargue los parámetros y vuelva a intentarlo");
        this.campos = Map.copyOf(campos);
    }

    public Map<String, String> campos() {
        return campos;
    }
}
