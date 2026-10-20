package com.maxiconecta.crm.segmentacion.historial;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/** SCRUM-569/291/292. Escritura transaccional y consulta de instantáneas históricas inmutables. */
@Repository
public class HistorialSegmentos {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public HistorialSegmentos(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(long auditoriaId) {
        jdbc.update("""
                INSERT INTO segmentacion.historial_segmento
                    (cliente_id, fecha, operacion, segmento_anterior, segmento_nuevo, regla,
                     criterios_evaluados, metricas, politica, motivo, id_evento, tipo_evento_origen,
                     regla_completa, auditoria_evento_id)
                SELECT entidad_id::bigint, ocurrido_en, operacion, detalle::jsonb #>> '{anterior,segmento}',
                       detalle::jsonb #>> '{nuevo,segmento}', detalle::jsonb -> 'regla',
                       detalle::jsonb -> 'criteriosEvaluados', detalle::jsonb -> 'metricas',
                       detalle::jsonb ->> 'politica', detalle::jsonb ->> 'motivo',
                       detalle::jsonb ->> 'idEvento', detalle::jsonb ->> 'tipoEventoOrigen', true, id
                FROM auditoria.evento WHERE id = ? AND entidad = 'SEGMENTO_CLIENTE'
                """, auditoriaId);
    }

    // El total y las filas ven la misma instantánea aunque se procese una compra durante la consulta.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Pagina consultar(long clienteId, int pagina, int tamanio) {
        long total = jdbc.queryForObject("SELECT count(*) FROM segmentacion.historial_segmento WHERE cliente_id = ?",
                Long.class, clienteId);
        var cambios = jdbc.query("""
                SELECT * FROM segmentacion.historial_segmento WHERE cliente_id = ?
                ORDER BY fecha DESC, id DESC LIMIT ? OFFSET ?
                """, (fila, n) -> new Cambio(fila.getLong("id"), fila.getLong("cliente_id"),
                fila.getObject("fecha", OffsetDateTime.class), fila.getString("operacion"),
                fila.getString("segmento_anterior"), fila.getString("segmento_nuevo"),
                leer(fila.getString("regla")), leer(fila.getString("criterios_evaluados")),
                leer(fila.getString("metricas")), fila.getString("politica"), fila.getString("motivo"),
                fila.getString("id_evento"), fila.getString("tipo_evento_origen"), fila.getBoolean("regla_completa")),
                clienteId, tamanio, (long) pagina * tamanio);
        return new Pagina(cambios, pagina, tamanio, total, (total + tamanio - 1) / tamanio);
    }

    private JsonNode leer(String valor) {
        if (valor == null) return null;
        try {
            return json.readTree(valor);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo leer una instantánea del historial", ex);
        }
    }

    public record Pagina(List<Cambio> contenido, int pagina, int tamanio, long totalElementos, long totalPaginas) { }
    public record Cambio(long id, long clienteId, OffsetDateTime fecha, String operacion,
                         String segmentoAnterior, String segmentoNuevo, JsonNode regla, JsonNode criteriosEvaluados,
                         JsonNode metricas, String politica, String motivo, String idEvento, String tipoEventoOrigen,
                         boolean reglaCompleta) { }
}
