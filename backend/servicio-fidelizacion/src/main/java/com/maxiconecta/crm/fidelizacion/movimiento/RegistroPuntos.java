package com.maxiconecta.crm.fidelizacion.movimiento;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Saldo de puntos (SCRUM-389) e historial de movimientos por transacción (SCRUM-390, SCRUM-403),
 * dentro de la transacción de {@link ProcesadorMovimientos}.
 */
@Repository
public class RegistroPuntos {

    private final JdbcTemplate jdbc;

    public RegistroPuntos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Reserva el movimiento antes de tocar el saldo (SCRUM-243): la clave primaria detiene una segunda
     * aplicación de la misma transacción aunque dos copias se procesen a la vez. En una redención
     * (SCRUM-403) el valor consumido puede faltar y no hay tasa aplicada.
     */
    public void reservar(String idTransaccion, TipoMovimiento tipo, long clienteId, String idClienteOrigen,
                         BigDecimal valorConsumido, long puntos, BigDecimal bolivianosPorPunto,
                         OffsetDateTime fechaTransaccion, long idEventoMovimiento) {
        try {
            jdbc.update("""
                    INSERT INTO fidelizacion.movimiento_puntos
                        (id_transaccion, tipo_movimiento, cliente_id, id_cliente_origen, valor_consumido, puntos,
                         bolivianos_por_punto, saldo_resultante, fecha_transaccion, id_evento_movimiento)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                    """, idTransaccion, tipo.name(), clienteId, idClienteOrigen, valorConsumido, puntos,
                    bolivianosPorPunto, fechaTransaccion, idEventoMovimiento);
        } catch (DuplicateKeyException ex) {
            throw new MovimientoDuplicadoException(tipo == TipoMovimiento.REDENCION
                    ? "La transacción " + idTransaccion + " ya descontó sus puntos (REDENCION)"
                    : "La transacción " + idTransaccion + " ya acreditó sus puntos (" + tipo.name() + ")");
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

    /**
     * Descuenta los puntos solo si el saldo alcanza (SCRUM-407): la condición y la resta van en la misma
     * sentencia, así que dos redenciones simultáneas del mismo cliente no pueden dejar el saldo negativo.
     * Devuelve el saldo resultante.
     *
     * @throws SaldoInsuficienteException si el cliente no tiene saldo o no le alcanza; el saldo no cambia
     */
    public long debitar(long clienteId, String idTransaccion, long puntos) {
        List<Long> saldo = jdbc.queryForList("""
                UPDATE fidelizacion.saldo_puntos SET saldo = saldo - ?, actualizado_en = now()
                WHERE cliente_id = ? AND saldo >= ?
                RETURNING saldo
                """, Long.class, puntos, clienteId, puntos);
        if (saldo.isEmpty()) {
            throw new SaldoInsuficienteException(idTransaccion, puntos, saldoDisponible(clienteId));
        }
        return saldo.get(0);
    }

    /** Saldo vigente del cliente; 0 si todavía no tiene saldo. */
    public long saldoDisponible(long clienteId) {
        return jdbc.queryForList("SELECT saldo FROM fidelizacion.saldo_puntos WHERE cliente_id = ?", Long.class, clienteId)
                .stream().findFirst().orElse(0L);
    }

    /** Deja al cliente en el nivel que corresponde a su saldo (criterio de aceptación 4 de SCRUM-48). */
    public void asignarNivel(long clienteId, String nivel) {
        jdbc.update("UPDATE fidelizacion.saldo_puntos SET nivel = ? WHERE cliente_id = ? AND nivel IS DISTINCT FROM ?",
                nivel, clienteId, nivel);
    }

    public void anotarSaldoResultante(String idTransaccion, TipoMovimiento tipo, long saldo) {
        jdbc.update("UPDATE fidelizacion.movimiento_puntos SET saldo_resultante = ? "
                + "WHERE id_transaccion = ? AND tipo_movimiento = ?", saldo, idTransaccion, tipo.name());
    }

    public List<DetalleMovimiento> movimientos(long clienteId) {
        return jdbc.query("""
                SELECT id_transaccion, tipo_movimiento, id_cliente_origen, valor_consumido, puntos, bolivianos_por_punto,
                       saldo_resultante, fecha_transaccion, registrado_en
                FROM fidelizacion.movimiento_puntos WHERE cliente_id = ? ORDER BY registrado_en DESC, fecha_transaccion DESC
                """, (r, n) -> new DetalleMovimiento(r.getString("id_transaccion"), r.getString("tipo_movimiento"),
                r.getString("id_cliente_origen"), r.getBigDecimal("valor_consumido"), r.getLong("puntos"),
                variacion(r.getString("tipo_movimiento"), r.getLong("puntos")),
                r.getBigDecimal("bolivianos_por_punto"), r.getLong("saldo_resultante"),
                r.getObject("fecha_transaccion", OffsetDateTime.class),
                r.getObject("registrado_en", OffsetDateTime.class)), clienteId);
    }

    /** Efecto del movimiento sobre el saldo: suma en una acumulación y resta en una redención (SCRUM-403). */
    static long variacion(String tipoMovimiento, long puntos) {
        return TipoMovimiento.REDENCION.name().equals(tipoMovimiento) ? -puntos : puntos;
    }

    /**
     * Detalle de puntos de una transacción (criterio de aceptación 4 de SCRUM-45). {@code puntos} es
     * siempre positivo y {@code variacionSaldo} indica si sumó o restó (SCRUM-403); una redención no
     * tiene tasa y su valor consumido es opcional.
     */
    public record DetalleMovimiento(String idTransaccion, String tipoMovimiento, String idClienteOrigen,
                                    BigDecimal valorConsumido, long puntos, long variacionSaldo,
                                    BigDecimal bolivianosPorPunto, long saldoResultante,
                                    OffsetDateTime fechaTransaccion, OffsetDateTime registradoEn) {
    }
}
