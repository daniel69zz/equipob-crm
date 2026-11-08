package com.maxiconecta.crm.fidelizacion.caducidad;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

/**
 * Movimientos de caducidad en el historial de puntos (SCRUM-417), dentro de la transacción que descuenta el
 * saldo del cliente.
 */
@Repository
public class RegistroCaducidad {

    private final JdbcTemplate jdbc;

    public RegistroCaducidad(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Registra la caducidad con su fecha de vencimiento como fecha de la transacción. No tiene evento, valor en
     * bolivianos ni regla de acumulación; el saldo resultante es 0. {@code idClienteOrigen} es el cliente de
     * Marketplace y Ventas de la última compra.
     *
     * @return {@code false} si ese vencimiento ya estaba registrado (criterio de aceptación 3)
     */
    public boolean registrar(String idTransaccion, long clienteId, String idClienteOrigen, long puntos,
                             OffsetDateTime fechaCaducidad) {
        // ON CONFLICT en vez de capturar la clave duplicada: en PostgreSQL ese error anularía la transacción del
        // movimiento que pidió la caducidad.
        return jdbc.update("""
                INSERT INTO fidelizacion.movimiento_puntos
                    (id_transaccion, tipo_movimiento, cliente_id, id_cliente_origen, puntos, saldo_resultante,
                     fecha_transaccion)
                VALUES (?, 'CADUCIDAD', ?, ?, ?, 0, ?)
                ON CONFLICT (id_transaccion, tipo_movimiento) DO NOTHING
                """, idTransaccion, clienteId, idClienteOrigen, puntos, fechaCaducidad) == 1;
    }
}
