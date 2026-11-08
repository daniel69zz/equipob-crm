package com.maxiconecta.crm.fidelizacion.nivel;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Asignación automática del nivel (SCRUM-411) con los puntos del periodo de evaluación (SCRUM-409).
 * <p>
 * Puntos del periodo = puntos de las acumulaciones con fecha de transacción dentro de los últimos
 * {@code periodo_meses} meses, menos las reversas (SCRUM-524) y las redenciones (SCRUM-48) del mismo
 * periodo, y los ajustes manuales con su signo (SCRUM-429), nunca menos de cero: después de una redención o
 * de una reversa el cliente queda en el nivel que corresponde a su nuevo saldo. Una reversa cuenta con la fecha de la compra que revierte (SCRUM-541): sale
 * del periodo junto con esa compra, y no le quita puntos a compras posteriores. El nivel es el del
 * umbral de la configuración vigente que contiene esos puntos, o ninguno si no alcanzan el umbral más bajo.
 * <p>
 * Todo se calcula en una sola sentencia en la base, para un cliente (dentro de la transacción que cambia
 * su saldo) o para todos (al cambiar la configuración y en el recálculo programado). Cada cambio de nivel
 * queda en {@code fidelizacion.cambio_nivel} con su origen (SCRUM-541).
 */
@Repository
public class AsignacionNiveles {

    private static final String SENTENCIA = """
            WITH config AS (
                SELECT version, periodo_meses FROM fidelizacion.configuracion_niveles ORDER BY version DESC LIMIT 1
            ), movimientos AS (
                SELECT m.cliente_id, m.tipo_movimiento, m.puntos, m.variacion_ajuste,
                       COALESCE(o.fecha_transaccion, m.fecha_transaccion) AS fecha
                FROM fidelizacion.movimiento_puntos m
                LEFT JOIN fidelizacion.movimiento_puntos o
                       ON o.id_transaccion = m.id_transaccion_revertida
                      AND o.tipo_movimiento = m.tipo_movimiento_revertido
            ), periodo AS (
                SELECT s.cliente_id, s.nivel AS anterior,
                       GREATEST(0, COALESCE(SUM(CASE m.tipo_movimiento WHEN 'ACUMULACION' THEN m.puntos
                                                                       WHEN 'REVERSA' THEN -m.puntos
                                                                       WHEN 'REDENCION' THEN -m.puntos
                                                                       WHEN 'AJUSTE' THEN m.variacion_ajuste ELSE 0 END), 0)) AS puntos
                FROM fidelizacion.saldo_puntos s
                CROSS JOIN config c
                LEFT JOIN movimientos m
                       ON m.cliente_id = s.cliente_id
                      AND m.fecha >= now() - make_interval(months => c.periodo_meses)
                %s
                GROUP BY s.cliente_id, s.nivel
            ), calculado AS (
                SELECT p.cliente_id, p.anterior, p.puntos, u.nivel
                FROM periodo p
                CROSS JOIN config c
                LEFT JOIN fidelizacion.umbral_nivel u
                       ON u.version = c.version AND p.puntos >= u.puntos_minimos
                      AND (u.puntos_maximos IS NULL OR p.puntos <= u.puntos_maximos)
            ), actualizado AS (
                UPDATE fidelizacion.saldo_puntos s
                SET puntos_periodo = k.puntos,
                    nivel = k.nivel,
                    nivel_actualizado_en = CASE WHEN s.nivel IS DISTINCT FROM k.nivel THEN now() ELSE s.nivel_actualizado_en END
                FROM calculado k
                WHERE s.cliente_id = k.cliente_id
                %s
                RETURNING s.cliente_id, k.anterior, k.nivel, k.puntos
            ), cambios AS (
                INSERT INTO fidelizacion.cambio_nivel
                    (cliente_id, nivel_anterior, nivel_nuevo, puntos_periodo, origen, id_transaccion, tipo_movimiento)
                SELECT cliente_id, anterior, nivel, puntos, ?::varchar, ?::varchar, ?::varchar FROM actualizado
                WHERE anterior IS DISTINCT FROM nivel
            )
            """;

    private static final String UN_CLIENTE = SENTENCIA.formatted("WHERE s.cliente_id = ?", "")
            + "SELECT nivel FROM actualizado";
    /** Solo toca a quien cambió; devuelve una fila por cliente con {@code true} si cambió de nivel. */
    private static final String TODOS = SENTENCIA.formatted("",
            "AND (s.nivel IS DISTINCT FROM k.nivel OR s.puntos_periodo <> k.puntos)")
            + "SELECT anterior IS DISTINCT FROM nivel FROM actualizado";

    private final JdbcTemplate jdbc;

    public AsignacionNiveles(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Recalcula el nivel de un cliente con saldo; devuelve su nivel, o nulo si no tiene. */
    public Nivel recalcular(long clienteId) {
        return recalcular(clienteId, null, null);
    }

    /**
     * Recalcula el nivel de un cliente por el movimiento que cambió su saldo, que queda como origen si el
     * nivel cambia (por ejemplo, la reversa que lo hace descender, SCRUM-541).
     *
     * @return su nivel, o nulo si no tiene
     */
    public Nivel recalcular(long clienteId, String idTransaccion, String tipoMovimiento) {
        return jdbc.query(UN_CLIENTE, r -> r.next() && r.getString(1) != null ? Nivel.valueOf(r.getString(1)) : null,
                clienteId, Origen.MOVIMIENTO.name(), idTransaccion, tipoMovimiento);
    }

    /**
     * Recalcula a todos los clientes con saldo y actualiza los puntos del periodo de los que cambiaron.
     *
     * @return cuántos clientes cambiaron de nivel
     */
    public int recalcularTodos() {
        return (int) jdbc.queryForList(TODOS, Boolean.class, Origen.RECALCULO.name(), null, null).stream()
                .filter(Boolean.TRUE::equals).count();
    }

    /** Cambios de nivel de un cliente, del más reciente al más antiguo (SCRUM-541). */
    public List<CambioNivel> cambios(long clienteId) {
        return jdbc.query("""
                SELECT nivel_anterior, nivel_nuevo, puntos_periodo, origen, id_transaccion, tipo_movimiento, cambiado_en
                FROM fidelizacion.cambio_nivel WHERE cliente_id = ? ORDER BY cambiado_en DESC, id DESC
                """, (r, n) -> new CambioNivel(r.getString("nivel_anterior"), r.getString("nivel_nuevo"),
                r.getLong("puntos_periodo"), Origen.valueOf(r.getString("origen")), r.getString("id_transaccion"),
                r.getString("tipo_movimiento"), r.getObject("cambiado_en", OffsetDateTime.class)), clienteId);
    }

    /** MOVIMIENTO: un movimiento de puntos del cliente. RECALCULO: cambio de umbrales o recálculo programado. */
    public enum Origen { MOVIMIENTO, RECALCULO }

    /** Un nivel nulo es «sin nivel». {@code idTransaccion} y {@code tipoMovimiento} solo en un MOVIMIENTO. */
    public record CambioNivel(String nivelAnterior, String nivelNuevo, long puntosPeriodo, Origen origen,
                              String idTransaccion, String tipoMovimiento, OffsetDateTime cambiadoEn) {
    }
}
