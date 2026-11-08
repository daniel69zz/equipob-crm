package com.maxiconecta.crm.fidelizacion.movimiento;

import com.maxiconecta.crm.fidelizacion.regla.ReglaAcumulacion;
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
     * aplicación de la misma transacción aunque dos copias se procesen a la vez. Guarda la versión de la
     * regla aplicada (SCRUM-46): un cambio posterior de la regla no recalcula estos puntos. Una redención
     * (SCRUM-403) no tiene regla ({@code regla} nulo) y su valor consumido puede faltar.
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
                    regla == null ? null : regla.bolivianosPorPunto(), regla == null ? null : regla.montoMinimo(),
                    regla == null ? null : regla.version(), fechaTransaccion,
                    idEventoMovimiento);
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

    public void anotarSaldoResultante(String idTransaccion, TipoMovimiento tipo, long saldo) {
        jdbc.update("UPDATE fidelizacion.movimiento_puntos SET saldo_resultante = ? "
                + "WHERE id_transaccion = ? AND tipo_movimiento = ?", saldo, idTransaccion, tipo.name());
    }

    public List<DetalleMovimiento> movimientos(long clienteId) {
        return jdbc.query("""
                SELECT id_transaccion, tipo_movimiento, id_cliente_origen, valor_consumido, puntos, bolivianos_por_punto,
                       monto_minimo, version_regla, saldo_resultante, fecha_transaccion, registrado_en,
                       id_transaccion_revertida, puntos_no_recuperados
                FROM fidelizacion.movimiento_puntos WHERE cliente_id = ? ORDER BY registrado_en DESC, fecha_transaccion DESC
                """, (r, n) -> new DetalleMovimiento(r.getString("id_transaccion"), r.getString("tipo_movimiento"),
                r.getString("id_cliente_origen"), r.getBigDecimal("valor_consumido"), r.getLong("puntos"),
                variacion(r.getString("tipo_movimiento"), r.getLong("puntos"), r.getLong("puntos_no_recuperados")),
                r.getBigDecimal("bolivianos_por_punto"), r.getBigDecimal("monto_minimo"),
                r.getObject("version_regla", Long.class), r.getLong("saldo_resultante"),
                r.getObject("fecha_transaccion", OffsetDateTime.class),
                r.getObject("registrado_en", OffsetDateTime.class), r.getString("id_transaccion_revertida"),
                r.getLong("puntos_no_recuperados")), clienteId);
    }

    /**
     * Efecto del movimiento sobre el saldo: suma en una acumulación, resta en una redención (SCRUM-403) y
     * en una reversa resta lo que se pudo descontar (SCRUM-524).
     */
    static long variacion(String tipoMovimiento, long puntos, long puntosNoRecuperados) {
        return switch (TipoMovimiento.valueOf(tipoMovimiento)) {
            case ACUMULACION -> puntos;
            case REDENCION -> -puntos;
            case REVERSA -> -(puntos - puntosNoRecuperados);
        };
    }

    /**
     * Detalle de puntos de una transacción (criterio de aceptación 4 de SCRUM-45), con la regla aplicada
     * (SCRUM-46). {@code puntos} es siempre positivo y {@code variacionSaldo} indica si sumó o restó
     * (SCRUM-403); una redención no tiene regla (tasa, monto mínimo y versión nulos) y su valor consumido
     * es opcional. Una reversa (SCRUM-539) trae la transacción que revierte, el monto revertido en
     * {@code valorConsumido}, la regla de la compra original y los puntos que no se pudieron descontar porque
     * el cliente ya los había redimido.
     */
    public record DetalleMovimiento(String idTransaccion, String tipoMovimiento, String idClienteOrigen,
                                    BigDecimal valorConsumido, long puntos, long variacionSaldo,
                                    BigDecimal bolivianosPorPunto, BigDecimal montoMinimo, Long versionRegla,
                                    long saldoResultante, OffsetDateTime fechaTransaccion,
                                    OffsetDateTime registradoEn, String idTransaccionRevertida,
                                    long puntosNoRecuperados) {
    }
}
