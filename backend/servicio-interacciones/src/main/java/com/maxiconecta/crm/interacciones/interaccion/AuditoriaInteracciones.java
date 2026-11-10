package com.maxiconecta.crm.interacciones.interaccion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maxiconecta.crm.interacciones.interaccion.ServicioInteracciones.Cambio;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Auditoría de la creación y de cada modificación de una interacción (SCRUM-436). Cada operación deja dos registros
 * en la misma transacción que el cambio, y si uno falla la interacción no se guarda:
 * <ul>
 *   <li>{@code interacciones.interaccion_auditoria}, de solo inserción, con los valores anteriores y nuevos;</li>
 *   <li>{@code auditoria.evento}, la auditoría compartida que consulta el Administrador, con la entidad
 *       {@code CLIENTE} y sin el texto de la descripción (docs/seguridad/alcance-auditoria.md).</li>
 * </ul>
 */
@Repository
public class AuditoriaInteracciones {

    static final String REGISTRADA = "INTERACCION_REGISTRADA";
    static final String MODIFICADA = "INTERACCION_MODIFICADA";
    static final String SEGUIMIENTO = "INTERACCION_SEGUIMIENTO_ACTUALIZADO";
    static final String COMPRA_VINCULADA = "INTERACCION_COMPRA_VINCULADA";
    static final String COMPRA_DESVINCULADA = "INTERACCION_COMPRA_DESVINCULADA";
    static final String ASIGNADA = "INTERACCION_ASIGNADA";
    static final String ENTIDAD_CLIENTE = "CLIENTE";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditoriaInteracciones(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** En la creación, los cambios son los valores iniciales (anterior nulo) de los campos que tienen valor. */
    public void creada(Interaccion interaccion) {
        Map<String, Cambio> iniciales = new LinkedHashMap<>();
        interaccion.editables().forEach((campo, valor) -> {
            if (valor != null) {
                iniciales.put(campo, new Cambio(null, valor));
            }
        });
        registrar(interaccion, "CREADA", interaccion.registradoPor(), interaccion.registradoRol(), iniciales, null,
                REGISTRADA);
    }

    public void modificada(Interaccion interaccion, String rol, Map<String, Cambio> cambios) {
        registrar(interaccion, "MODIFICADA", interaccion.modificadoPor(), rol, cambios, null, MODIFICADA);
    }

    /** Un cambio de estado o de tipo (SCRUM-55, criterio de aceptación 1), con su comentario si lo tiene. */
    public void seguimiento(Interaccion interaccion, String usuario, String rol, Map<String, Cambio> cambios,
                            String comentario) {
        registrar(interaccion, "SEGUIMIENTO", usuario, rol, cambios, comentario, SEGUIMIENTO);
    }

    /**
     * Una compra vinculada, reemplazada o quitada (SCRUM-448): quién lo hizo, cuándo, y la referencia anterior y la
     * nueva (criterio de aceptación 3 de SCRUM-54).
     */
    public void compra(Interaccion interaccion, String usuario, String rol, String anterior, String nueva) {
        boolean vinculada = nueva != null;
        registrar(interaccion, vinculada ? "COMPRA_VINCULADA" : "COMPRA_DESVINCULADA", usuario, rol,
                Map.of("compra", new Cambio(anterior, nueva)), null,
                vinculada ? COMPRA_VINCULADA : COMPRA_DESVINCULADA,
                Map.of("compra", vinculada ? nueva : anterior));
    }

    /**
     * Una asignación o reasignación del responsable (SCRUM-458): quién la hizo, cuándo, el responsable anterior y el
     * nuevo, y el comentario (criterio de aceptación 2 de SCRUM-56).
     */
    public void asignada(Interaccion interaccion, String usuario, String rol, String anterior, String nuevo,
                         String comentario) {
        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("responsable", nuevo);
        if (anterior != null) {
            extra.put("responsableAnterior", anterior);
        }
        registrar(interaccion, "ASIGNADA", usuario, rol, Map.of("responsable", new Cambio(anterior, nuevo)),
                comentario, ASIGNADA, extra);
    }

    /** Los registros de una interacción, del primero al último. */
    public List<RegistroAuditoria> de(long interaccionId) {
        return jdbc.query("SELECT id, operacion, usuario, rol, ocurrido_en, version, cambios::text, comentario "
                        + "FROM interacciones.interaccion_auditoria WHERE interaccion_id = ? ORDER BY id",
                (rs, n) -> new RegistroAuditoria(rs.getLong("id"), rs.getString("operacion"), rs.getString("usuario"),
                        rs.getString("rol"), rs.getObject("ocurrido_en", OffsetDateTime.class), rs.getLong("version"),
                        leer(rs.getString("cambios")), rs.getString("comentario")),
                interaccionId);
    }

    private void registrar(Interaccion interaccion, String operacion, String usuario, String rol,
                           Map<String, Cambio> cambios, String comentario, String operacionCompartida) {
        registrar(interaccion, operacion, usuario, rol, cambios, comentario, operacionCompartida, Map.of());
    }

    /** {@code extra} son datos que no son personales y se agregan al detalle de la auditoría compartida. */
    private void registrar(Interaccion interaccion, String operacion, String usuario, String rol,
                           Map<String, Cambio> cambios, String comentario, String operacionCompartida,
                           Map<String, String> extra) {
        jdbc.update("INSERT INTO interacciones.interaccion_auditoria (interaccion_id, operacion, usuario, rol, version, "
                        + "cambios, comentario) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)", interaccion.id(), operacion,
                usuario, rol, interaccion.version(), escribir(cambios), comentario);

        ObjectNode detalle = json.createObjectNode();
        detalle.put("idInteraccion", interaccion.id());
        detalle.put("tipo", interaccion.tipo());
        detalle.put("version", interaccion.version());
        detalle.put("rol", rol);
        if (interaccion.estado() != null) {
            detalle.put("estado", interaccion.estado());
        }
        extra.forEach(detalle::put);
        ArrayNode campos = detalle.putArray("campos");
        cambios.keySet().forEach(campos::add);
        jdbc.update("INSERT INTO auditoria.evento(usuario, operacion, entidad, entidad_id, detalle) VALUES (?, ?, ?, ?, ?)",
                usuario, operacionCompartida, ENTIDAD_CLIENTE, String.valueOf(interaccion.clienteId()),
                detalle.toString());
    }

    private String escribir(Map<String, Cambio> cambios) {
        try {
            return json.writeValueAsString(cambios);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudieron serializar los cambios de la interacción", ex);
        }
    }

    private Map<String, Cambio> leer(String cambios) {
        try {
            return json.readValue(cambios, new TypeReference<LinkedHashMap<String, Cambio>>() { });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudieron leer los cambios de la interacción", ex);
        }
    }

    /** Un registro de la auditoría de una interacción. */
    public record RegistroAuditoria(long id, String operacion, String usuario, String rol, OffsetDateTime ocurridoEn,
                                    long version, Map<String, Cambio> cambios, String comentario) {
    }
}
