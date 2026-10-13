package com.maxiconecta.crm.segmentacion.criterio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class CriterioRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<Criterio> MAPPER = (r, n) -> new Criterio(
            r.getLong("id"), r.getString("nombre"), r.getString("descripcion"),
            Criterio.Indicador.valueOf(r.getString("indicador")), Criterio.Condicion.valueOf(r.getString("condicion")),
            r.getBigDecimal("minimo"), r.getBigDecimal("maximo"), r.getString("categoria"), r.getBoolean("activo"),
            r.getLong("version"), r.getObject("creado_en", OffsetDateTime.class),
            r.getObject("actualizado_en", OffsetDateTime.class), r.getString("actualizado_por"));

    public CriterioRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Serializa validar + escribir, incluso con la tabla vacía y varios administradores concurrentes. */
    public void bloquearConfiguracion() { jdbc.execute("LOCK TABLE segmentacion.criterio IN SHARE ROW EXCLUSIVE MODE"); }

    public List<Criterio> listar() { return jdbc.query("SELECT * FROM segmentacion.criterio ORDER BY id DESC", MAPPER); }

    /** Criterios vigentes para la asignación automática (SCRUM-26), del más antiguo al más nuevo. */
    public List<Criterio> activos() {
        return jdbc.query("SELECT * FROM segmentacion.criterio WHERE activo ORDER BY id", MAPPER);
    }

    public Optional<Criterio> obtener(long id) {
        return jdbc.query("SELECT * FROM segmentacion.criterio WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public Criterio crear(SolicitudCriterio s, String usuario) {
        return jdbc.queryForObject("""
                INSERT INTO segmentacion.criterio
                  (nombre, nombre_clave, descripcion, indicador, condicion, minimo, maximo, categoria, categoria_clave, actualizado_por)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING *
                """, MAPPER, s.nombre(), ReglasCriterio.clave(s.nombre()), s.descripcion(), s.indicador().name(),
                s.condicion().name(), s.minimo(), s.maximo(), s.categoria(),
                s.categoria() == null ? null : ReglasCriterio.clave(s.categoria()), usuario);
    }

    public Criterio editar(long id, SolicitudCriterio s, String usuario) {
        return jdbc.queryForObject("""
                UPDATE segmentacion.criterio SET nombre=?, nombre_clave=?, descripcion=?, indicador=?, condicion=?,
                  minimo=?, maximo=?, categoria=?, categoria_clave=?, version=version+1, actualizado_en=now(), actualizado_por=?
                WHERE id=? RETURNING *
                """, MAPPER, s.nombre(), ReglasCriterio.clave(s.nombre()), s.descripcion(), s.indicador().name(),
                s.condicion().name(), s.minimo(), s.maximo(), s.categoria(),
                s.categoria() == null ? null : ReglasCriterio.clave(s.categoria()), usuario, id);
    }

    public Criterio cambiarEstado(long id, boolean activo, String usuario) {
        return jdbc.queryForObject("""
                UPDATE segmentacion.criterio SET activo=?, version=version+1, actualizado_en=now(), actualizado_por=?
                WHERE id=? RETURNING *
                """, MAPPER, activo, usuario, id);
    }
}
