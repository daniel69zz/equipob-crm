package com.maxiconecta.crm.fidelizacion.movimiento;

import com.maxiconecta.crm.fidelizacion.regla.ReglaAcumulacion;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Saldo de puntos (SCRUM-389) y detalle por transacción (SCRUM-390), dentro de la transacción de
 * {@link ProcesadorMovimientos}.
 */
@Repository
public class RegistroPuntos {

    private final JdbcTemplate jdbc;

    public RegistroPuntos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Reserva el movimiento antes de tocar el saldo (SCRUM-243): la clave primaria detiene una segunda
     * acreditación de la misma transacción aunque dos copias se procesen a la vez. Guarda la versión de la
     * regla aplicada (SCRUM-46): un cambio posterior de la regla no recalcula estos puntos.
     */
    public void reservar(String idTransaccion, TipoMovimiento tipo, long clienteId, String idClienteOrigen,
                         BigDecimal valorConsumido, long puntos, ReglaAcumulacion regla,
                         OffsetDateTime fechaTransaccion, long idEventoMovimiento) {
        try {
            jdbc.update("""
                    INSERT INTO fidelizacion.movimiento_puntos
                        (id_transaccion, tipo_movimiento, cliente_id, id_cliente_origen, valor_consumido, puntos,
                         bolivianos_por_punto, monto_minimo, version_regla, saldo_resultante, fecha_transaccion,
                         id_evento_movimiento)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                    """, idTransaccion, tipo.name(), clienteId, idClienteOrigen, valorConsumido, puntos,
                    regla.bolivianosPorPunto(), regla.montoMinimo(), regla.version(), fechaTransaccion,
                    idEventoMovimiento);
        } catch (DuplicateKeyException ex) {
            throw new MovimientoDuplicadoException("La transacción " + idTransaccion + " ya acreditó sus puntos ("
                    + tipo.name() + ")");
        }
    }

    /** Suma atómica al saldo; un cliente nuevo queda con saldo y sin nivel. Devuelve el saldo resultante. */
    public long acreditar(long clienteId, long puntos) {
        return jdbc.queryForObject("""
                INSERT INTO fidelizacion.saldo_puntos AS s (cliente_id, saldo, actualizado_en)
                VALUES (?, ?, now())
                ON CONFLICT (cliente_id) DO UPDATE SET saldo = s.saldo + EXCLUDED.saldo, actualizado_en = now()
                RETURNING saldo
                """, Long.class, clienteId, puntos);
    }

    public void anotarSaldoResultante(String idTransaccion, TipoMovimiento tipo, long saldo) {
        jdbc.update("UPDATE fidelizacion.movimiento_puntos SET saldo_resultante = ? "
                + "WHERE id_transaccion = ? AND tipo_movimiento = ?", saldo, idTransaccion, tipo.name());
    }

    public List<DetalleMovimiento> movimientos(long clienteId) {
        return jdbc.query("""
                SELECT id_transaccion, tipo_movimiento, id_cliente_origen, valor_consumido, puntos, bolivianos_por_punto,
                       monto_minimo, version_regla, saldo_resultante, fecha_transaccion, registrado_en
                FROM fidelizacion.movimiento_puntos WHERE cliente_id = ? ORDER BY registrado_en DESC, fecha_transaccion DESC
                """, (r, n) -> new DetalleMovimiento(r.getString("id_transaccion"), r.getString("tipo_movimiento"),
                r.getString("id_cliente_origen"), r.getBigDecimal("valor_consumido"), r.getLong("puntos"),
                r.getBigDecimal("bolivianos_por_punto"), r.getBigDecimal("monto_minimo"), r.getLong("version_regla"),
                r.getLong("saldo_resultante"),
                r.getObject("fecha_transaccion", OffsetDateTime.class),
                r.getObject("registrado_en", OffsetDateTime.class)), clienteId);
    }

    /** Detalle de puntos de una transacción (criterio de aceptación 4), con la regla aplicada (SCRUM-46). */
    public record DetalleMovimiento(String idTransaccion, String tipoMovimiento, String idClienteOrigen,
                                    BigDecimal valorConsumido, long puntos, BigDecimal bolivianosPorPunto,
                                    BigDecimal montoMinimo, long versionRegla, long saldoResultante, OffsetDateTime fechaTransaccion,
                                    OffsetDateTime registradoEn) {
    }
}
