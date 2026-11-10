package com.maxiconecta.crm.interacciones.notificacion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Persistencia de {@code interacciones.notificacion} (SCRUM-459). */
@Repository
public class NotificacionesUsuario {

    private static final String COLUMNAS = "id, tipo, interaccion_id, cliente_id, mensaje, comentario, creada_por, "
            + "creada_en, leida_en";

    private static final RowMapper<Notificacion> FILA = (rs, n) -> new Notificacion(rs.getLong("id"),
            rs.getString("tipo"), rs.getLong("interaccion_id"), rs.getLong("cliente_id"), rs.getString("mensaje"),
            rs.getString("comentario"), rs.getString("creada_por"), rs.getObject("creada_en", OffsetDateTime.class),
            rs.getObject("leida_en", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public NotificacionesUsuario(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Se llama dentro de la transacción del cambio que la origina: si el cambio no se guarda, ella tampoco. */
    public Notificacion crear(String destinatario, String tipo, long interaccionId, long clienteId, String mensaje,
                              String comentario, String creadaPor) {
        return jdbc.queryForObject("INSERT INTO interacciones.notificacion (destinatario, tipo, interaccion_id, "
                        + "cliente_id, mensaje, comentario, creada_por) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING "
                        + COLUMNAS, FILA, destinatario, tipo, interaccionId, clienteId, mensaje, comentario,
                creadaPor);
    }

    /** Las del usuario, de la más reciente a la más antigua. */
    public List<Notificacion> de(String destinatario, boolean soloNoLeidas, int limite) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM interacciones.notificacion WHERE destinatario = ?"
                        + (soloNoLeidas ? " AND leida_en IS NULL" : "") + " ORDER BY id DESC LIMIT ?", FILA,
                destinatario, limite);
    }

    public long noLeidas(String destinatario) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM interacciones.notificacion "
                + "WHERE destinatario = ? AND leida_en IS NULL", Long.class, destinatario);
        return total == null ? 0 : total;
    }

    /** La marca como leída, solo si es del usuario; si ya lo estaba, conserva la fecha en que se leyó. */
    public Optional<Notificacion> marcarLeida(long id, String destinatario) {
        return jdbc.query("UPDATE interacciones.notificacion SET leida_en = coalesce(leida_en, now()) "
                + "WHERE id = ? AND destinatario = ? RETURNING " + COLUMNAS, FILA, id, destinatario)
                .stream().findFirst();
    }
}
