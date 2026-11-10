package com.maxiconecta.crm.segmentacion.reporte;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuenta los clientes por segmento y por nivel de consumo (SCRUM-463 y SCRUM-460) agrupando en la
 * base lo que ya guarda la segmentación, las mismas dos tablas del listado por segmento
 * ({@code consulta.ListadoClientesRepository}): el segmento vigente y la última foto de métricas.
 * El nivel de fidelización no está aquí: lo da servicio-fidelizacion ({@link NivelesFidelizacion}).
 */
@Repository
public class DistribucionRepository {

    /**
     * Población del reporte: todos los clientes que la segmentación conoce. Igual que en el listado,
     * se cruzan con FULL JOIN porque un cliente puede tener métricas sin segmento (no cumple ningún
     * criterio) o segmento sin métricas (asignaciones anteriores a SCRUM-26).
     */
    private static final String CLIENTES = """
            SELECT COALESCE(s.cliente_id, m.cliente_id) AS cliente_id, s.segmento, m.nivel_consumo
            FROM segmentacion.segmento_cliente s
            FULL JOIN segmentacion.metricas_cliente m ON m.cliente_id = s.cliente_id
            """;

    private static final RowMapper<Conteo> CONTEO =
            (r, n) -> new Conteo(r.getString("valor"), r.getLong("clientes"));

    private final JdbcTemplate jdbc;

    public DistribucionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Cuántos clientes entran en el reporte; 0 cuando el filtro no deja ninguno (criterio 2). */
    public long total(FiltroReporte filtro) {
        Condiciones condiciones = Condiciones.de(filtro);
        Long total = jdbc.queryForObject("SELECT count(*)" + condiciones.desde(), Long.class,
                condiciones.valores());
        return total == null ? 0 : total;
    }

    /**
     * Clientes por segmento, de mayor a menor y por nombre a igual cantidad; el grupo sin segmento va
     * al final (criterio de aceptación 1).
     */
    public List<Conteo> porSegmento(FiltroReporte filtro) {
        Condiciones condiciones = Condiciones.de(filtro);
        return jdbc.query("SELECT segmento AS valor, count(*) AS clientes" + condiciones.desde()
                + " GROUP BY segmento ORDER BY (segmento IS NULL), count(*) DESC, lower(segmento)",
                CONTEO, condiciones.valores());
    }

    /** Clientes por nivel de consumo, de BAJO a ALTO; el grupo sin nivel va al final. */
    public List<Conteo> porNivelConsumo(FiltroReporte filtro) {
        Condiciones condiciones = Condiciones.de(filtro);
        return jdbc.query("SELECT nivel_consumo AS valor, count(*) AS clientes" + condiciones.desde()
                + " GROUP BY nivel_consumo ORDER BY CASE nivel_consumo WHEN 'BAJO' THEN 0 WHEN 'MEDIO' THEN 1"
                + " WHEN 'ALTO' THEN 2 ELSE 3 END", CONTEO, condiciones.valores());
    }

    /**
     * Identificadores de los clientes del reporte, para pedir sus niveles de fidelización. Solo se
     * usa cuando hay filtro: sin filtro, fidelización cuenta a todos sus clientes sin enumerarlos.
     */
    public List<Long> clientes(FiltroReporte filtro) {
        Condiciones condiciones = Condiciones.de(filtro);
        return jdbc.queryForList("SELECT cliente_id" + condiciones.desde() + " ORDER BY cliente_id", Long.class,
                condiciones.valores());
    }

    /** Un grupo del reporte y su cantidad; {@code valor} nulo es el grupo sin asignar. */
    public record Conteo(String valor, long clientes) {
    }

    /** Parte WHERE común a los tres conteos y al total, construida desde el filtro. */
    private record Condiciones(String where, Object[] valores) {

        static Condiciones de(FiltroReporte filtro) {
            List<String> condiciones = new ArrayList<>();
            List<Object> valores = new ArrayList<>();
            if (filtro.segmento() != null) {
                condiciones.add("segmento = ?");
                valores.add(filtro.segmento());
            }
            if (filtro.nivelConsumo() != null) {
                condiciones.add("nivel_consumo = ?");
                valores.add(filtro.nivelConsumoNombre());
            }
            String where = condiciones.isEmpty() ? "" : " WHERE " + String.join(" AND ", condiciones);
            return new Condiciones(where, valores.toArray());
        }

        String desde() {
            return " FROM (" + CLIENTES + ") clientes" + where;
        }
    }
}
