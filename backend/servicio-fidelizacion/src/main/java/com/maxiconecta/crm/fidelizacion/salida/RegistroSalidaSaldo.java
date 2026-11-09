package com.maxiconecta.crm.fidelizacion.salida;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** SCRUM-618: conserva el saldo y nivel del movimiento, nunca los de una consulta posterior. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class RegistroSalidaSaldo {
    private final JdbcTemplate jdbc;

    public RegistroSalidaSaldo(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void registrar(long clienteId, String transaccion, String tipo) {
        UUID evento = UUID.randomUUID();
        int insertados = jdbc.update("""
                INSERT INTO fidelizacion.salida_saldo_puntos
                    (id_evento, cliente_id, id_transaccion, tipo_movimiento, contenido)
                SELECT ?, s.cliente_id, m.id_transaccion, m.tipo_movimiento,
                    jsonb_build_object('idEvento', ?::text, 'tipoEvento', 'SALDO_PUNTOS_ACTUALIZADO',
                        'fechaEmision', now(), 'idCliente', m.id_cliente_origen,
                        'idTransaccion', m.id_transaccion, 'tipoMovimiento', m.tipo_movimiento,
                        'puntos', m.puntos, 'variacionSaldo', CASE m.tipo_movimiento
                            WHEN 'ACUMULACION' THEN m.puntos WHEN 'AJUSTE' THEN m.variacion_ajuste
                            WHEN 'REVERSA' THEN -(m.puntos - m.puntos_no_recuperados) ELSE -m.puntos END,
                        'saldo', s.saldo, 'nivel', s.nivel)
                FROM fidelizacion.saldo_puntos s JOIN fidelizacion.movimiento_puntos m
                  ON m.cliente_id = s.cliente_id
                WHERE s.cliente_id = ? AND m.id_transaccion = ? AND m.tipo_movimiento = ?
                """, evento, evento.toString(), clienteId, transaccion, tipo);
        if (insertados != 1) {
            throw new IllegalStateException("No se pudo registrar el evento del movimiento " + transaccion);
        }
    }

    public void registrarCambioNivel(long clienteId) {
        UUID evento = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO fidelizacion.salida_saldo_puntos (id_evento, cliente_id, tipo_movimiento, contenido)
                SELECT ?, cliente_id, 'RECALCULO_NIVEL',
                    jsonb_build_object('idEvento', ?::text, 'tipoEvento', 'SALDO_PUNTOS_ACTUALIZADO',
                        'fechaEmision', now(), 'idCliente', NULL, 'idTransaccion', NULL,
                        'tipoMovimiento', 'RECALCULO_NIVEL', 'puntos', 0, 'variacionSaldo', 0,
                        'saldo', saldo, 'nivel', nivel)
                FROM fidelizacion.saldo_puntos WHERE cliente_id = ?
                """, evento, evento.toString(), clienteId);
    }
}
