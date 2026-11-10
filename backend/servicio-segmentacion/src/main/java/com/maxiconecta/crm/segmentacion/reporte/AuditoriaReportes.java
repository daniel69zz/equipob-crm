package com.maxiconecta.crm.segmentacion.reporte;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Registra en la auditoría compartida de solo inserción quién generó el reporte de distribución de
 * clientes y con qué alcance (SCRUM-57, docs/seguridad/alcance-auditoria.md). Complementa el
 * registro del API Gateway, que guarda el acceso a la ruta pero no sus parámetros. El detalle no
 * lleva datos personales: solo el filtro, el tamaño de la población y si fidelización respondió.
 */
@Repository
public class AuditoriaReportes {

    static final String OPERACION = "REPORTE_DISTRIBUCION_GENERADO";
    static final String ENTIDAD = "REPORTE";
    static final String ENTIDAD_ID = "DISTRIBUCION_CLIENTES";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditoriaReportes(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Se llama antes de responder: si el registro falla, el reporte falla y los datos no se muestran,
     * igual que en el listado de clientes por segmento.
     */
    public void registrar(String usuario, FiltroReporte filtro, long total, boolean fidelizacionDisponible) {
        ObjectNode detalle = json.createObjectNode();
        detalle.put("segmento", filtro.segmento());
        detalle.put("nivelConsumo", filtro.nivelConsumoNombre());
        detalle.put("total", total);
        detalle.put("sinDatos", total == 0);
        detalle.put("fidelizacionDisponible", fidelizacionDisponible);
        jdbc.update("""
                INSERT INTO auditoria.evento(usuario, operacion, entidad, entidad_id, detalle)
                VALUES (?, ?, ?, ?, ?)
                """, usuario, OPERACION, ENTIDAD, ENTIDAD_ID, detalle.toString());
    }
}
