package com.maxiconecta.crm.segmentacion.asignacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maxiconecta.crm.segmentacion.criterio.Criterio;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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

    public AuditoriaSegmentos(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void registrar(Operacion operacion, long clienteId, RegistroSegmentos.SegmentoActual anterior,
                          Criterio nuevo, MetricasCliente metricas) {
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
        ObjectNode usadas = detalle.putObject("metricas");
        usadas.put("frecuencia", metricas.frecuencia());
        usadas.put("valor", metricas.valor());
        usadas.set("categorias", json.valueToTree(metricas.categoriasOVacio()));
        usadas.put("clasificacionFrecuencia", metricas.clasificacionFrecuencia());
        usadas.put("calculadasEn", metricas.calculadasEn().toString());
        detalle.put("idEvento", metricas.idEvento());
        detalle.put("tipoEventoOrigen", metricas.tipoEventoOrigen());
        jdbc.update("""
                INSERT INTO auditoria.evento(usuario, operacion, entidad, entidad_id, detalle)
                VALUES (?, ?, ?, ?, ?)
                """, USUARIO_SISTEMA, operacion.name(), ENTIDAD, Long.toString(clienteId), detalle.toString());
    }

    public enum Operacion { SEGMENTO_ASIGNADO, SEGMENTO_CAMBIADO, SEGMENTO_RETIRADO }
}
