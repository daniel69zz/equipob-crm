package com.maxiconecta.crm.interacciones.interaccion;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Una consulta, un reclamo o una nota del historial unificado del cliente, con su estado de seguimiento
 * (SCRUM-55): quién lo cambió por última vez y cuándo; la compra del cliente vinculada, o nula (SCRUM-54); y el
 * agente responsable, con quién lo asignó y cuándo, o nulos si no tiene (SCRUM-56).
 */
public record Interaccion(long id, long clienteId, String tipo, String canal, String asunto, String descripcion,
                          String referenciaCompra, String estado, String estadoActualizadoPor,
                          OffsetDateTime estadoActualizadoEn, String registradoPor, String registradoRol,
                          OffsetDateTime registradoEn, String modificadoPor, OffsetDateTime modificadoEn,
                          long version, CompraVinculada compra, String responsable, String responsableAsignadoPor,
                          OffsetDateTime responsableAsignadoEn) {

    @JsonIgnore
    public boolean cerrada() {
        return EstadoInteraccion.CERRADA.name().equals(estado);
    }

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
