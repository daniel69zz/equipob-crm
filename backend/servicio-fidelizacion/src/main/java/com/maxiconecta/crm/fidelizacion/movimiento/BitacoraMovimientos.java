package com.maxiconecta.crm.fidelizacion.movimiento;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Bitácora de los movimientos recibidos (criterio de aceptación 3). Cada escritura es su propia
 * transacción, para que el registro de un mensaje no se pierda aunque su procesamiento falle.
 */
@Repository
public class BitacoraMovimientos {

    private static final RowMapper<EventoMovimiento> MAPPER = (r, n) -> new EventoMovimiento(r.getLong("id"),
            r.getString("contenido"), r.getString("id_evento"), r.getString("id_transaccion"),
            r.getString("tipo_movimiento"), Estado.valueOf(r.getString("estado")), r.getString("causa"),
            r.getObject("recibido_en", OffsetDateTime.class), r.getObject("procesado_en", OffsetDateTime.class),
            r.getInt("intentos"));

    private final JdbcTemplate jdbc;

    public BitacoraMovimientos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long registrar(String contenido, LectorMovimientos.Cabecera cabecera) {
        return jdbc.queryForObject("""
                INSERT INTO fidelizacion.evento_movimiento (contenido, id_evento, id_transaccion, tipo_movimiento, estado)
                VALUES (?, ?, ?, ?, 'RECIBIDO') RETURNING id
                """, Long.class, contenido, cabecera.idEvento(), cabecera.idTransaccion(), cabecera.tipoMovimiento());
    }

    /** Dentro de la transacción del procesamiento: queda PROCESADO junto con los puntos, o nada. */
    public void marcarProcesado(long id) {
        jdbc.update("UPDATE fidelizacion.evento_movimiento SET estado = 'PROCESADO', causa = NULL, procesado_en = now() "
                + "WHERE id = ?", id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcar(long id, Estado estado, String causa) {
        jdbc.update("UPDATE fidelizacion.evento_movimiento SET estado = ?, causa = ?, procesado_en = now() WHERE id = ?",
                estado.name(), causa, id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void contarIntento(long id) {
        jdbc.update("UPDATE fidelizacion.evento_movimiento SET intentos = intentos + 1 WHERE id = ?", id);
    }

    public Optional<EventoMovimiento> obtener(long id) {
        return jdbc.query("SELECT * FROM fidelizacion.evento_movimiento WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public List<EventoMovimiento> listar(Estado estado, int limite) {
        return estado == null
                ? jdbc.query("SELECT * FROM fidelizacion.evento_movimiento ORDER BY id DESC LIMIT ?", MAPPER, limite)
                : jdbc.query("SELECT * FROM fidelizacion.evento_movimiento WHERE estado = ? ORDER BY id DESC LIMIT ?",
                        MAPPER, estado.name(), limite);
    }

    public enum Estado { RECIBIDO, PROCESADO, FALLIDO, DESCARTADO }

    public record EventoMovimiento(long id, String contenido, String idEvento, String idTransaccion,
                                   String tipoMovimiento, Estado estado, String causa, OffsetDateTime recibidoEn,
                                   OffsetDateTime procesadoEn, int intentos) {
    }
}
