package com.maxiconecta.crm.fidelizacion.ajuste;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Ajustes manuales de puntos (SCRUM-422): el saldo, el movimiento {@code AJUSTE} del historial y el registro de
 * solo inserción {@code fidelizacion.ajuste_puntos} (SCRUM-421), dentro de la transacción del ajuste.
 */
@Repository
public class RegistroAjustes {

    private static final RowMapper<Ajuste> AJUSTE = (r, n) -> new Ajuste(r.getLong("id"), r.getString("id_transaccion"),
            r.getLong("cliente_id"), r.getLong("puntos"), r.getLong("saldo_anterior"), r.getLong("saldo_resultante"),
            r.getString("motivo"), r.getString("referencia"), r.getString("usuario"), r.getString("rol"),
            r.getObject("registrado_en", OffsetDateTime.class), null);

    private final JdbcTemplate jdbc;

    public RegistroAjustes(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean yaAplicado(UUID idSolicitud) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM fidelizacion.ajuste_puntos WHERE id_solicitud = ?)", Boolean.class, idSolicitud));
    }

    /**
     * Saldo del cliente, bloqueado hasta el fin de la transacción. Un cliente sin cuenta de puntos queda con una en
     * 0, también bloqueada, para que dos primeros ajustes simultáneos se apliquen de a uno.
     */
    public long bloquearSaldo(long clienteId) {
        jdbc.update("""
                INSERT INTO fidelizacion.saldo_puntos (cliente_id, saldo, actualizado_en) VALUES (?, 0, now())
                ON CONFLICT (cliente_id) DO NOTHING
                """, clienteId);
        return jdbc.queryForObject("SELECT saldo FROM fidelizacion.saldo_puntos WHERE cliente_id = ? FOR UPDATE",
                Long.class, clienteId);
    }

    public void actualizarSaldo(long clienteId, long saldo) {
        jdbc.update("UPDATE fidelizacion.saldo_puntos SET saldo = ?, actualizado_en = now() WHERE cliente_id = ?",
                saldo, clienteId);
    }

    /** El ajuste en el historial de movimientos: {@code puntos} es el valor absoluto y la variación lleva el signo. */
    public void registrarMovimiento(String idTransaccion, long clienteId, long puntos, long saldoResultante) {
        jdbc.update("""
                INSERT INTO fidelizacion.movimiento_puntos
                    (id_transaccion, tipo_movimiento, cliente_id, puntos, variacion_ajuste, saldo_resultante,
                     fecha_transaccion)
                VALUES (?, 'AJUSTE', ?, ?, ?, ?, now())
                """, idTransaccion, clienteId, Math.abs(puntos), puntos, saldoResultante);
    }

    /** SCRUM-421: motivo, responsable y fecha del ajuste. La fecha la pone la base. */
    public Ajuste registrar(UUID idSolicitud, String idTransaccion, long clienteId, long puntos, long saldoAnterior,
                            long saldoResultante, String motivo, String referencia, String usuario, String rol) {
        return jdbc.queryForObject("""
                INSERT INTO fidelizacion.ajuste_puntos
                    (id_solicitud, id_transaccion, cliente_id, puntos, saldo_anterior, saldo_resultante, motivo,
                     referencia, usuario, rol)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING *
                """, AJUSTE, idSolicitud, idTransaccion, clienteId, puntos, saldoAnterior, saldoResultante, motivo,
                referencia, usuario, rol);
    }

    /** Ajustes del cliente, del más reciente al más antiguo (criterio de aceptación 4). */
    public List<Ajuste> deCliente(long clienteId) {
        return jdbc.query("SELECT * FROM fidelizacion.ajuste_puntos WHERE cliente_id = ? "
                + "ORDER BY registrado_en DESC, id DESC", AJUSTE, clienteId);
    }
}
