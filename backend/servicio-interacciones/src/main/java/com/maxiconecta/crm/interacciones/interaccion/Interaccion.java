package com.maxiconecta.crm.interacciones.interaccion;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Una consulta, un reclamo o una nota del historial unificado del cliente. */
public record Interaccion(long id, long clienteId, String tipo, String canal, String asunto, String descripcion,
                          String referenciaCompra, String registradoPor, String registradoRol,
                          OffsetDateTime registradoEn, String modificadoPor, OffsetDateTime modificadoEn,
                          long version) {

    /** Los campos editables, en el orden en que se muestran; se usan para comparar versiones. */
    @JsonIgnore
    public Map<String, String> editables() {
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("tipo", tipo);
        campos.put("canal", canal);
        campos.put("asunto", asunto);
        campos.put("descripcion", descripcion);
        campos.put("referenciaCompra", referenciaCompra);
        return campos;
    }
}
