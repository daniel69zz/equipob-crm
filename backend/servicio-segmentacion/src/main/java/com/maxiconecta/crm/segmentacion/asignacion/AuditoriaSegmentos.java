package com.maxiconecta.crm.segmentacion.asignacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maxiconecta.crm.segmentacion.criterio.Criterio;
import com.maxiconecta.crm.segmentacion.historial.HistorialSegmentos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * Registra cada cambio de segmento en la auditoría compartida de solo inserción, en la misma
 * transacción que el cambio (SCRUM-265). Es la base del historial de cambios de SCRUM-30.
 */
@Repository
public class AuditoriaSegmentos {

    static final String USUARIO_SISTEMA = "sistema";
    static final String ENTIDAD = "SEGMENTO_CLIENTE";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final HistorialSegmentos historial;

    public AuditoriaSegmentos(JdbcTemplate jdbc, ObjectMapper json, HistorialSegmentos historial) {
        this.jdbc = jdbc;
        this.json = json;
        this.historial = historial;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(Operacion operacion, long clienteId, RegistroSegmentos.SegmentoActual anterior,
                          Criterio nuevo, MetricasCliente metricas, List<Criterio> evaluados) {
        ObjectNode detalle = json.createObjectNode();
        detalle.set("anterior", anterior == null ? null : json.valueToTree(anterior));
        if (nuevo == null) {
            detalle.putNull("nuevo");
        } else {
            ObjectNode segmento = detalle.putObject("nuevo");
            segmento.put("segmento", nuevo.nombre());
            segmento.put("criterioId", nuevo.id());
            segmento.put("indicador", nuevo.indicador().name());
        }
        // Serializar los records completos también conserva campos nuevos del contrato de métricas/reglas.
        detalle.set("regla", nuevo == null ? json.nullNode() : json.valueToTree(nuevo));
        if (nuevo == null) detalle.set("criteriosEvaluados", json.valueToTree(evaluados));
        detalle.put("politica", "VALOR_FRECUENCIA_CATEGORIA_V1");
        detalle.put("motivo", nuevo == null ? "SIN_CRITERIO_COINCIDENTE" : "CRITERIO_COINCIDENTE");
        ObjectNode usadas = json.valueToTree(metricas);
        usadas.set("categorias", json.valueToTree(metricas.categoriasOVacio()));
        detalle.set("metricas", usadas);
        detalle.put("idEvento", metricas.idEvento());
        detalle.put("tipoEventoOrigen", metricas.tipoEventoOrigen());
        Long auditoriaId = jdbc.queryForObject("""
                INSERT INTO auditoria.evento(usuario, operacion, entidad, entidad_id, detalle)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, USUARIO_SISTEMA, operacion.name(), ENTIDAD, Long.toString(clienteId), detalle.toString());
        historial.registrar(auditoriaId);
    }

    public enum Operacion { SEGMENTO_ASIGNADO, SEGMENTO_CAMBIADO, SEGMENTO_RETIRADO }
}
