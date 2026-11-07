package com.maxiconecta.crm.fidelizacion.movimiento;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Confirmación de una redención por su transacción de origen (SCRUM-406). Marketplace y Ventas publica
 * la redención sin esperar respuesta; con esta consulta confirma al cliente si se aplicó y con qué
 * saldo quedó, o por qué no se aplicó.
 */
@Repository
public class ConfirmacionRedenciones {

    private final JdbcTemplate jdbc;

    public ConfirmacionRedenciones(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return vacío si nunca llegó una redención con esa transacción */
    public Optional<Confirmacion> confirmar(String idTransaccion) {
        Optional<Confirmacion> aplicada = jdbc.query("""
                SELECT m.id_transaccion, m.id_cliente_origen, m.puntos, m.saldo_resultante, m.fecha_transaccion,
                       m.registrado_en, m.id_evento_movimiento, s.nivel
                FROM fidelizacion.movimiento_puntos m
                LEFT JOIN fidelizacion.saldo_puntos s ON s.cliente_id = m.cliente_id
                WHERE m.id_transaccion = ? AND m.tipo_movimiento = 'REDENCION'
                """, (r, n) -> new Confirmacion(r.getString("id_transaccion"), EstadoRedencion.APLICADA, null,
                r.getString("id_cliente_origen"), r.getLong("puntos"), r.getLong("saldo_resultante"),
                r.getString("nivel"), r.getObject("fecha_transaccion", OffsetDateTime.class),
                r.getObject("registrado_en", OffsetDateTime.class), r.getLong("id_evento_movimiento")),
                idTransaccion).stream().findFirst();
        if (aplicada.isPresent()) {
            return aplicada;
        }
        // Sin movimiento aplicado: el último intento de la bitácora dice si falló o si sigue en proceso.
        return jdbc.query("""
                SELECT id, estado, causa FROM fidelizacion.evento_movimiento
                WHERE id_transaccion = ? AND tipo_movimiento = 'REDENCION'
                ORDER BY id DESC LIMIT 1
                """, (r, n) -> {
            boolean fallida = BitacoraMovimientos.Estado.FALLIDO.name().equals(r.getString("estado"));
            return new Confirmacion(idTransaccion, fallida ? EstadoRedencion.FALLIDA : EstadoRedencion.EN_PROCESO,
                    fallida ? r.getString("causa") : null, null, null, null, null, null, null, r.getLong("id"));
        }, idTransaccion).stream().findFirst();
    }

    /**
     * APLICADA: los puntos se descontaron. FALLIDA: no se aplicó (por ejemplo, saldo insuficiente) y se
     * puede reprocesar. EN_PROCESO: recibida y todavía sin resultado.
     */
    public enum EstadoRedencion { APLICADA, FALLIDA, EN_PROCESO }

    /** {@code idEvento} es el evento de la bitácora, para reprocesarla si quedó FALLIDA. */
    public record Confirmacion(String idTransaccion, EstadoRedencion estado, String causa, String idClienteOrigen,
                               Long puntos, Long saldoResultante, String nivel, OffsetDateTime fechaTransaccion,
                               OffsetDateTime registradaEn, Long idEvento) {
    }
}
