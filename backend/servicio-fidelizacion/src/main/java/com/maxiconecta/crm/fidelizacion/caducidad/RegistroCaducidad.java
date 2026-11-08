package com.maxiconecta.crm.fidelizacion.caducidad;

import com.maxiconecta.crm.fidelizacion.movimiento.RegistroPuntos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Movimientos de caducidad en el historial de puntos (SCRUM-417) y descuento del saldo vencido (SCRUM-414),
 * dentro de la transacción que caduca los puntos del cliente.
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

    /**
     * SCRUM-415: clientes con saldo cuya última compra con puntos es lo bastante antigua para que el saldo pueda
     * estar vencido en {@code corte}. Los meses se restan en la base con unos días de margen, porque sumar meses
     * en Java (la regla) y restarlos en PostgreSQL no coincide a fin de mes; la decisión final la toma
     * {@link ProcesoCaducidad} con {@link ReglaCaducidad}, con el saldo bloqueado.
     */
    public List<Long> candidatos(OffsetDateTime corte, int vigenciaMeses) {
        return jdbc.queryForList("""
                SELECT s.cliente_id
                FROM fidelizacion.saldo_puntos s
                JOIN LATERAL (
                    SELECT max(m.fecha_transaccion) AS ultima FROM fidelizacion.movimiento_puntos m
                    WHERE m.cliente_id = s.cliente_id AND (%s)
                ) u ON true
                WHERE s.saldo > 0
                  AND u.ultima <= ?::timestamptz - make_interval(months => ?) + interval '4 days'
                ORDER BY s.cliente_id
                """.formatted(RegistroPuntos.RENUEVA_VIGENCIA), Long.class, corte, vigenciaMeses);
    }

    /**
     * Saldo del cliente, bloqueado hasta el fin de la transacción: mientras se caduca, ningún movimiento del mismo
     * cliente puede cambiarlo, y dos caducidades simultáneas se aplican de a una.
     */
    public Optional<Long> bloquearSaldo(long clienteId) {
        return jdbc.queryForList("SELECT saldo FROM fidelizacion.saldo_puntos WHERE cliente_id = ? FOR UPDATE",
                Long.class, clienteId).stream().findFirst();
    }

    /**
     * Último movimiento que renovó la vigencia del saldo (SCRUM-418): una compra con puntos o un ajuste positivo
     * (SCRUM-429). Un ajuste no tiene cliente de Ventas.
     */
    public Optional<UltimaCompra> ultimaCompra(long clienteId) {
        List<UltimaCompra> compra = jdbc.query("""
                SELECT fecha_transaccion, id_cliente_origen FROM fidelizacion.movimiento_puntos
                WHERE cliente_id = ? AND (%s)
                ORDER BY fecha_transaccion DESC LIMIT 1
                """.formatted(RegistroPuntos.RENUEVA_VIGENCIA), (r, n) -> new UltimaCompra(r.getObject("fecha_transaccion", OffsetDateTime.class),
                r.getString("id_cliente_origen")), clienteId);
        return compra.stream().findFirst();
    }

    /** SCRUM-414: al caducar se descuenta todo el saldo. */
    public void vaciarSaldo(long clienteId) {
        jdbc.update("UPDATE fidelizacion.saldo_puntos SET saldo = 0, actualizado_en = now() WHERE cliente_id = ?",
                clienteId);
    }

    /**
     * Si los puntos de esa compra ya caducaron: hubo una caducidad del cliente después de la fecha de la compra,
     * y esa caducidad descontó todo el saldo de entonces.
     */
    public boolean caducaronLosPuntosDe(String idCompra) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM fidelizacion.movimiento_puntos c
                    JOIN fidelizacion.movimiento_puntos o
                      ON o.id_transaccion = ? AND o.tipo_movimiento = 'ACUMULACION' AND o.cliente_id = c.cliente_id
                    WHERE c.tipo_movimiento = 'CADUCIDAD' AND c.fecha_transaccion > o.fecha_transaccion)
                """, Boolean.class, idCompra));
    }

    public record UltimaCompra(OffsetDateTime fecha, String idClienteOrigen) {
    }
}
