package com.maxiconecta.crm.fidelizacion.parametro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/**
 * SCRUM-521: cada cambio de un parámetro general queda en la auditoría compartida de solo inserción
 * ({@code auditoria.evento}) con el valor anterior, el nuevo, quién y cuándo (criterio de aceptación 3). Se registra
 * en la transacción del cambio: si la auditoría falla, el parámetro no cambia. La consulta de auditoría del
 * Administrador (SCRUM-521, {@code /api/admin/auditoria}) lo muestra junto con los demás eventos.
 */
@Repository
public class AuditoriaParametros {

    static final String OPERACION = "PARAMETRO_GENERAL_MODIFICADO";
    static final String ENTIDAD = "PARAMETRO_GENERAL";
    static final String SERVICIO = "servicio-fidelizacion";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditoriaParametros(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Un cambio registrado, tal como lo muestra el historial de la pantalla de parámetros. */
    public record CambioRegistrado(OffsetDateTime fecha, String usuario, String codigo, String nombre, String unidad,
                                   BigDecimal valorAnterior, BigDecimal valorNuevo, String servicio) {
    }

    public void registrar(ParametrosGenerales.Modificacion cambio) {
        ObjectNode detalle = json.createObjectNode();
        detalle.put("codigo", cambio.definicion().codigo());
        detalle.put("nombre", cambio.definicion().nombre());
        detalle.put("unidad", cambio.definicion().unidad());
        detalle.put("valorAnterior", cambio.anterior());
        detalle.put("valorNuevo", cambio.nuevo());
        detalle.put("servicio", SERVICIO);
        jdbc.update("""
                INSERT INTO auditoria.evento(ocurrido_en, usuario, operacion, entidad, entidad_id, detalle)
                VALUES (?, ?, ?, ?, ?, ?)
                """, cambio.fecha(), cambio.usuario(), OPERACION, ENTIDAD, cambio.definicion().codigo(),
                detalle.toString());
    }

    /** Los últimos cambios de estos parámetros, del más reciente al más antiguo. */
    public List<CambioRegistrado> historial(Collection<String> codigos, int limite) {
        return jdbc.query("""
                SELECT ocurrido_en, usuario, entidad_id, detalle FROM auditoria.evento
                WHERE operacion = ? AND entidad = ? AND entidad_id = ANY (?)
                ORDER BY ocurrido_en DESC, id DESC LIMIT ?
                """, this::cambio, OPERACION, ENTIDAD, codigos.toArray(String[]::new), limite);
    }

    private CambioRegistrado cambio(ResultSet rs, int fila) throws SQLException {
        try {
            JsonNode detalle = json.readTree(rs.getString("detalle"));
            return new CambioRegistrado(rs.getObject("ocurrido_en", OffsetDateTime.class), rs.getString("usuario"),
                    rs.getString("entidad_id"), detalle.path("nombre").asText(null), detalle.path("unidad").asText(null),
                    detalle.path("valorAnterior").decimalValue(), detalle.path("valorNuevo").decimalValue(),
                    detalle.path("servicio").asText(null));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Detalle de auditoría ilegible en el cambio de " + rs.getString("entidad_id"), e);
        }
    }
}
