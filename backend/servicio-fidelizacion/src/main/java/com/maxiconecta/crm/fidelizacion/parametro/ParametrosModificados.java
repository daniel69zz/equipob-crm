package com.maxiconecta.crm.fidelizacion.parametro;

import java.util.Set;

/**
 * Se publica después de guardar un cambio de parámetros generales (SCRUM-65), para que lo que se calculó con el
 * valor anterior se vuelva a calcular de inmediato en lugar de esperar al proceso nocturno.
 */
public record ParametrosModificados(Set<String> codigos) {

    public boolean incluye(String codigo) {
        return codigos.contains(codigo);
    }
}
