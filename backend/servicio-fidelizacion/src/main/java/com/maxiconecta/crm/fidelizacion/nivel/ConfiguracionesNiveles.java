package com.maxiconecta.crm.fidelizacion.nivel;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Versiones de la configuración de niveles: {@code fidelizacion.configuracion_niveles} y sus umbrales en
 * {@code fidelizacion.umbral_nivel}. La vigente es la de mayor versión; las demás son su historial.
 */
@Repository
public class ConfiguracionesNiveles {

    private static final String CONSULTA = """
            SELECT c.version, c.periodo_meses, c.motivo, c.modificado_por, c.vigente_desde,
                   u.nivel, u.puntos_minimos, u.puntos_maximos
            FROM fidelizacion.configuracion_niveles c
            JOIN fidelizacion.umbral_nivel u ON u.version = c.version
            """;

    private final JdbcTemplate jdbc;

    public ConfiguracionesNiveles(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ConfiguracionNiveles vigente() {
        List<ConfiguracionNiveles> vigente = leer(CONSULTA
                + " WHERE c.version = (SELECT max(version) FROM fidelizacion.configuracion_niveles)"
                + " ORDER BY u.puntos_minimos");
        if (vigente.isEmpty()) {
            throw new IllegalStateException("No hay una configuración de niveles de fidelización");
        }
        return vigente.get(0);
    }

    /** De la versión más reciente a la más antigua. */
    public List<ConfiguracionNiveles> historial() {
        return leer(CONSULTA + " ORDER BY c.version DESC, u.puntos_minimos");
    }

    /**
     * Inserta la versión indicada con sus umbrales. La clave primaria impide que dos administradores
     * guarden a la vez la misma versión ({@link org.springframework.dao.DuplicateKeyException}).
     */
    public void guardar(long version, int periodoMeses, List<UmbralNivel> umbrales, String motivo, String usuario) {
        jdbc.update("""
                INSERT INTO fidelizacion.configuracion_niveles (version, periodo_meses, motivo, modificado_por)
                VALUES (?, ?, ?, ?)
                """, version, periodoMeses, motivo, usuario);
        for (UmbralNivel umbral : umbrales) {
            jdbc.update("""
                    INSERT INTO fidelizacion.umbral_nivel (version, nivel, puntos_minimos, puntos_maximos)
                    VALUES (?, ?, ?, ?)
                    """, version, umbral.nivel().name(), umbral.puntosMinimos(), umbral.puntosMaximos());
        }
    }

    private List<ConfiguracionNiveles> leer(String sql) {
        Map<Long, Fila> versiones = new LinkedHashMap<>();
        jdbc.query(sql, (ResultSet r) -> {
            Fila fila = versiones.computeIfAbsent(r.getLong("version"), v -> cabecera(r));
            fila.umbrales.add(new UmbralNivel(Nivel.valueOf(r.getString("nivel")), r.getLong("puntos_minimos"),
                    r.getObject("puntos_maximos", Long.class)));
        });
        return versiones.values().stream().map(f -> new ConfiguracionNiveles(f.version, f.periodoMeses, f.umbrales,
                f.motivo, f.modificadoPor, f.vigenteDesde)).toList();
    }

    private static Fila cabecera(ResultSet r) {
        try {
            return new Fila(r.getLong("version"), r.getInt("periodo_meses"), r.getString("motivo"),
                    r.getString("modificado_por"), r.getObject("vigente_desde", OffsetDateTime.class));
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static final class Fila {
        final long version;
        final int periodoMeses;
        final String motivo;
        final String modificadoPor;
        final OffsetDateTime vigenteDesde;
        final List<UmbralNivel> umbrales = new ArrayList<>();

        Fila(long version, int periodoMeses, String motivo, String modificadoPor, OffsetDateTime vigenteDesde) {
            this.version = version;
            this.periodoMeses = periodoMeses;
            this.motivo = motivo;
            this.modificadoPor = modificadoPor;
            this.vigenteDesde = vigenteDesde;
        }
    }
}
