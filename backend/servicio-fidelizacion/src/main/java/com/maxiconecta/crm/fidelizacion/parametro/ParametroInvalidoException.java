package com.maxiconecta.crm.fidelizacion.parametro;

import java.util.Map;

/** Un valor fuera de rango o un cambio que no se puede aplicar: 400 con el motivo por parámetro (SCRUM-65). */
public class ParametroInvalidoException extends RuntimeException {

    private final Map<String, String> campos;

    public ParametroInvalidoException(String mensaje, Map<String, String> campos) {
        super(mensaje);
        this.campos = Map.copyOf(campos);
    }

    public Map<String, String> campos() {
        return campos;
    }
}
