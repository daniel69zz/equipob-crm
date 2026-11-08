package com.maxiconecta.crm.fidelizacion.regla;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Almacenamiento de las reglas de acumulación (SCRUM-393): una fila por versión en
 * {@code fidelizacion.regla_acumulacion}. La vigente es la de mayor versión; las demás son su historial.
 */
@Repository
public class ReglasAcumulacion {

    private static final RowMapper<ReglaAcumulacion> REGLA = (r, n) -> new ReglaAcumulacion(r.getLong("version"),
            r.getBigDecimal("bolivianos_por_punto"), r.getBigDecimal("monto_minimo"), r.getString("motivo"),
            r.getString("modificado_por"), r.getObject("vigente_desde", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public ReglasAcumulacion(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ReglaAcumulacion vigente() {
        return jdbc.query("SELECT * FROM fidelizacion.regla_acumulacion ORDER BY version DESC LIMIT 1", REGLA)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No hay una regla de acumulación de puntos configurada"));
    }

    /**
     * Inserta la versión indicada. La clave primaria impide que dos administradores guarden a la vez la
     * misma versión: el segundo recibe {@link org.springframework.dao.DuplicateKeyException}.
     */
    public ReglaAcumulacion guardar(long version, BigDecimal bolivianosPorPunto, BigDecimal montoMinimo, String motivo,
                                    String usuario) {
        return jdbc.queryForObject("""
                INSERT INTO fidelizacion.regla_acumulacion (version, bolivianos_por_punto, monto_minimo, motivo, modificado_por)
                VALUES (?, ?, ?, ?, ?) RETURNING *
                """, REGLA, version, bolivianosPorPunto, montoMinimo, motivo, usuario);
    }

    /** Versiones de la más reciente a la más antigua, cada una con los valores que reemplazó. */
    public List<CambioRegla> historial() {
        return jdbc.query("""
                SELECT r.*,
                       lag(bolivianos_por_punto) OVER (ORDER BY version) AS anterior_bolivianos_por_punto,
                       lag(monto_minimo) OVER (ORDER BY version) AS anterior_monto_minimo
                FROM fidelizacion.regla_acumulacion r ORDER BY version DESC
                """, (r, n) -> new CambioRegla(REGLA.mapRow(r, n),
                r.getBigDecimal("anterior_bolivianos_por_punto"), r.getBigDecimal("anterior_monto_minimo")));
    }

    /** Una versión del historial (criterio de aceptación 3); los valores anteriores son nulos en la primera. */
    public record CambioRegla(@JsonUnwrapped ReglaAcumulacion regla, BigDecimal anteriorBolivianosPorPunto,
                              BigDecimal anteriorMontoMinimo) {
    }
}
