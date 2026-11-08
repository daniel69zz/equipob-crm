package com.maxiconecta.crm.fidelizacion.comun;

import java.util.Map;

/** La operación choca con el estado actual (por ejemplo, otra persona cambió la configuración): 409. */
public class ConflictoException extends RuntimeException {

    private final Map<String, String> campos;

    public ConflictoException(String mensaje) {
        this(mensaje, Map.of());
    }

    public ConflictoException(String mensaje, Map<String, String> campos) {
        super(mensaje);
        this.campos = Map.copyOf(campos);
    }

    public Map<String, String> campos() {
        return campos;
    }
}
