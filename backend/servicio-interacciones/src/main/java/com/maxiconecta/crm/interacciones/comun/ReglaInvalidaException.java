package com.maxiconecta.crm.interacciones.comun;

import java.util.Map;

/** Una solicitud que no cumple las reglas del negocio: 400 con el campo y el motivo. */
public class ReglaInvalidaException extends RuntimeException {

    private final Map<String, String> campos;

    public ReglaInvalidaException(String mensaje) {
        this(mensaje, Map.of());
    }

    public ReglaInvalidaException(String mensaje, Map<String, String> campos) {
        super(mensaje);
        this.campos = Map.copyOf(campos);
    }

    public Map<String, String> campos() {
        return campos;
    }
}
