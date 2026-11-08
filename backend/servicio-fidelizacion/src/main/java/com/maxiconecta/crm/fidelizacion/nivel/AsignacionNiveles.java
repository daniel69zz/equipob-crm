package com.maxiconecta.crm.fidelizacion.nivel;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Asignación automática del nivel (SCRUM-411) con los puntos del periodo de evaluación (SCRUM-409).
 * <p>
 * Puntos del periodo = puntos de las acumulaciones con fecha de transacción dentro de los últimos
 * {@code periodo_meses} meses, menos las reversas (SCRUM-524) y las redenciones (SCRUM-48) del mismo
 * periodo, nunca menos de cero: después de una redención el cliente queda en el nivel que corresponde a su
 * nuevo saldo (criterio 4 de SCRUM-48). El nivel es el del
 * umbral de la configuración vigente que contiene esos puntos, o ninguno si no alcanzan el umbral más bajo.
 * <p>
 * Todo se calcula en una sola sentencia en la base, para un cliente (dentro de la transacción que cambia
 * su saldo) o para todos (al cambiar la configuración y en el recálculo programado).
 */
@Repository
public class AsignacionNiveles {

    private static final String SENTENCIA = """
            WITH config AS (
                SELECT version, periodo_meses FROM fidelizacion.configuracion_niveles ORDER BY version DESC LIMIT 1
            ), periodo AS (
                SELECT s.cliente_id, s.nivel AS anterior,
                       GREATEST(0, COALESCE(SUM(CASE m.tipo_movimiento WHEN 'ACUMULACION' THEN m.puntos
                                                                       WHEN 'REVERSA' THEN -m.puntos
                                                                       WHEN 'REDENCION' THEN -m.puntos ELSE 0 END), 0)) AS puntos
                FROM fidelizacion.saldo_puntos s
                CROSS JOIN config c
                LEFT JOIN fidelizacion.movimiento_puntos m
                       ON m.cliente_id = s.cliente_id
                      AND m.fecha_transaccion >= now() - make_interval(months => c.periodo_meses)
                %s
                GROUP BY s.cliente_id, s.nivel
            ), calculado AS (
                SELECT p.cliente_id, p.anterior, p.puntos, u.nivel
                FROM periodo p
                CROSS JOIN config c
                LEFT JOIN fidelizacion.umbral_nivel u
                       ON u.version = c.version AND p.puntos >= u.puntos_minimos
                      AND (u.puntos_maximos IS NULL OR p.puntos <= u.puntos_maximos)
            )
            UPDATE fidelizacion.saldo_puntos s
            SET puntos_periodo = k.puntos,
                nivel = k.nivel,
                nivel_actualizado_en = CASE WHEN s.nivel IS DISTINCT FROM k.nivel THEN now() ELSE s.nivel_actualizado_en END
            FROM calculado k
            WHERE s.cliente_id = k.cliente_id
            """;

    private static final String UN_CLIENTE = SENTENCIA.formatted("WHERE s.cliente_id = ?") + " RETURNING s.nivel";
    /** Solo toca a quien cambió; devuelve una fila por cliente con {@code true} si cambió de nivel. */
    private static final String TODOS = SENTENCIA.formatted("")
            + " AND (s.nivel IS DISTINCT FROM k.nivel OR s.puntos_periodo <> k.puntos)"
            + " RETURNING k.anterior IS DISTINCT FROM k.nivel";

    private final JdbcTemplate jdbc;

    public AsignacionNiveles(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Recalcula el nivel de un cliente con saldo; devuelve su nivel, o nulo si no tiene. */
    public Nivel recalcular(long clienteId) {
        return jdbc.query(UN_CLIENTE, r -> r.next() && r.getString(1) != null ? Nivel.valueOf(r.getString(1)) : null,
                clienteId);
    }

    /**
     * Recalcula a todos los clientes con saldo y actualiza los puntos del periodo de los que cambiaron.
     *
     * @return cuántos clientes cambiaron de nivel
     */
    public int recalcularTodos() {
        return (int) jdbc.queryForList(TODOS, Boolean.class).stream().filter(Boolean.TRUE::equals).count();
    }
}
