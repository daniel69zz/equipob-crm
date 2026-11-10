package com.maxiconecta.crm.interacciones.agente;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Agentes de Atención al Cliente que pueden ser responsables de una interacción (SCRUM-457). Los usuarios los
 * administra el API Gateway en {@code seguridad.usuario}; este servicio solo los lee, de la misma base
 * (docs/interacciones/asignacion-interacciones.md).
 */
@Repository
public class AgentesAtencion {

    static final String ROL_AGENTE = "AGENTE_ATENCION";

    private static final String CONSULTA = "SELECT u.nombre_usuario, u.nombre_completo FROM seguridad.usuario u "
            + "JOIN seguridad.rol r ON r.id = u.rol_id WHERE u.activo AND r.codigo = '" + ROL_AGENTE + "'";

    private final JdbcTemplate jdbc;

    public AgentesAtencion(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Los agentes activos, ordenados por nombre. */
    public List<Agente> activos() {
        return jdbc.query(CONSULTA + " ORDER BY u.nombre_completo, u.nombre_usuario",
                (rs, n) -> new Agente(rs.getString("nombre_usuario"), rs.getString("nombre_completo")));
    }

    /** El agente activo con ese usuario, o vacío si no existe, está inactivo o tiene otro rol. */
    public Optional<Agente> activo(String usuario) {
        return jdbc.query(CONSULTA + " AND u.nombre_usuario = ?",
                (rs, n) -> new Agente(rs.getString("nombre_usuario"), rs.getString("nombre_completo")), usuario)
                .stream().findFirst();
    }

    /** Un agente que se puede elegir como responsable. */
    public record Agente(String usuario, String nombreCompleto) {
    }
}
