package com.maxiconecta.crm.segmentacion.criterio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.List;

/** Solo inserta/consulta en la auditoría compartida, en la misma transacción que el criterio. */
@Repository
public class AuditoriaCriterios {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public AuditoriaCriterios(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public void registrar(String operacion, String usuario, Criterio anterior, Criterio nuevo) {
        var foto = json.createObjectNode();
        foto.set("anterior", json.valueToTree(anterior));
        foto.set("nuevo", json.valueToTree(nuevo));
        jdbc.update("""
                INSERT INTO auditoria.evento(usuario, operacion, entidad, entidad_id, detalle)
                VALUES (?, ?, 'CRITERIO_SEGMENTACION', ?, ?)
                """, usuario, operacion, nuevo.id().toString(), foto.toString());
    }

    public List<Cambio> historial(long id) {
        return jdbc.query("""
                SELECT id, ocurrido_en, usuario, operacion, detalle FROM auditoria.evento
                WHERE entidad='CRITERIO_SEGMENTACION' AND entidad_id=? ORDER BY ocurrido_en DESC, id DESC
                """, (r, n) -> {
            try {
                var fotos = json.readTree(r.getString("detalle"));
                return new Cambio(r.getLong("id"), r.getObject("ocurrido_en", OffsetDateTime.class),
                        r.getString("usuario"), r.getString("operacion"), fotos.get("anterior"), fotos.get("nuevo"));
            } catch (JsonProcessingException e) { throw new IllegalStateException("Auditoría de criterio ilegible", e); }
        }, Long.toString(id));
    }

    public record Cambio(long id, OffsetDateTime fecha, String usuario, String operacion, JsonNode anterior, JsonNode nuevo) { }
}
