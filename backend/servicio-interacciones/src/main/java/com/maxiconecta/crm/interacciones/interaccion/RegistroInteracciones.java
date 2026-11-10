package com.maxiconecta.crm.interacciones.interaccion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Persistencia de {@code interacciones.interaccion} (SCRUM-432). */
@Repository
public class RegistroInteracciones {

    private static final String COLUMNAS = "id, cliente_id, tipo, canal, asunto, descripcion, referencia_compra, "
            + "estado, estado_actualizado_por, estado_actualizado_en, registrado_por, registrado_rol, registrado_en, "
            + "modificado_por, modificado_en, version, compra_referencia, compra_fecha, compra_monto_total, "
            + "compra_estado, compra_vinculada_por, compra_vinculada_en";

    private static final RowMapper<Interaccion> FILA = (rs, n) -> new Interaccion(rs.getLong("id"),
            rs.getLong("cliente_id"), rs.getString("tipo"), rs.getString("canal"), rs.getString("asunto"),
            rs.getString("descripcion"), rs.getString("referencia_compra"), rs.getString("estado"),
            rs.getString("estado_actualizado_por"), rs.getObject("estado_actualizado_en", OffsetDateTime.class),
            rs.getString("registrado_por"), rs.getString("registrado_rol"),
            rs.getObject("registrado_en", OffsetDateTime.class),
            rs.getString("modificado_por"), rs.getObject("modificado_en", OffsetDateTime.class), rs.getLong("version"),
            compra(rs));

    private final JdbcTemplate jdbc;

    public RegistroInteracciones(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Interaccion registrar(long clienteId, Campos campos, String usuario, String rol) {
        return jdbc.queryForObject("INSERT INTO interacciones.interaccion (cliente_id, tipo, canal, asunto, descripcion, "
                        + "referencia_compra, registrado_por, registrado_rol) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                        + "RETURNING " + COLUMNAS, FILA, clienteId, campos.tipo(), campos.canal(), campos.asunto(),
                campos.descripcion(), campos.referenciaCompra(), usuario, rol);
    }

    /** La interacción, solo si es del cliente indicado en la ruta. */
    public Optional<Interaccion> buscar(long clienteId, long id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM interacciones.interaccion WHERE id = ? AND cliente_id = ?",
                FILA, id, clienteId).stream().findFirst();
    }

    /** La interacción bloqueada hasta el fin de la transacción, para que dos ediciones se apliquen de a una. */
    public Optional<Interaccion> bloquear(long clienteId, long id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM interacciones.interaccion WHERE id = ? AND cliente_id = ? "
                + "FOR UPDATE", FILA, id, clienteId).stream().findFirst();
    }

    public Interaccion modificar(long id, Campos campos, String usuario) {
        return jdbc.queryForObject("UPDATE interacciones.interaccion SET tipo = ?, canal = ?, asunto = ?, "
                        + "descripcion = ?, referencia_compra = ?, modificado_por = ?, modificado_en = now(), "
                        + "version = version + 1 WHERE id = ? RETURNING " + COLUMNAS, FILA, campos.tipo(),
                campos.canal(), campos.asunto(), campos.descripcion(), campos.referenciaCompra(), usuario, id);
    }

    /** Estado y tipo nuevos; si el estado cambia, también quién y cuándo (SCRUM-452). */
    public Interaccion actualizarSeguimiento(long id, String tipo, String estado, boolean cambiaEstado,
                                             String usuario) {
        return jdbc.queryForObject("UPDATE interacciones.interaccion SET tipo = ?, estado = ?, "
                        + "estado_actualizado_por = CASE WHEN ? THEN ? ELSE estado_actualizado_por END, "
                        + "estado_actualizado_en = CASE WHEN ? THEN now() ELSE estado_actualizado_en END, "
                        + "version = version + 1 WHERE id = ? RETURNING " + COLUMNAS, FILA, tipo, estado,
                cambiaEstado, usuario, cambiaEstado, id);
    }

    /** Vincula la compra, o reemplaza la que tenía, con quién y cuándo (SCRUM-445). */
    public Interaccion vincularCompra(long id, String referencia, OffsetDateTime fecha, BigDecimal montoTotal,
                                      String estado, String usuario) {
        return jdbc.queryForObject("UPDATE interacciones.interaccion SET compra_referencia = ?, compra_fecha = ?, "
                        + "compra_monto_total = ?, compra_estado = ?, compra_vinculada_por = ?, "
                        + "compra_vinculada_en = now(), version = version + 1 WHERE id = ? RETURNING " + COLUMNAS,
                FILA, referencia, fecha, montoTotal, estado, usuario, id);
    }

    /** Quita la compra vinculada. */
    public Interaccion desvincularCompra(long id) {
        return jdbc.queryForObject("UPDATE interacciones.interaccion SET compra_referencia = NULL, compra_fecha = NULL, "
                + "compra_monto_total = NULL, compra_estado = NULL, compra_vinculada_por = NULL, "
                + "compra_vinculada_en = NULL, version = version + 1 WHERE id = ? RETURNING " + COLUMNAS, FILA, id);
    }

    public PaginaInteracciones historial(long clienteId, FiltroHistorial filtro) {
        StringBuilder donde = new StringBuilder(" FROM interacciones.interaccion WHERE cliente_id = ?");
        List<Object> parametros = new ArrayList<>(List.of(clienteId));
        if (filtro.tipo() != null) {
            donde.append(" AND tipo = ?");
            parametros.add(filtro.tipo());
        }
        if (filtro.estado() != null) {
            donde.append(" AND estado = ?");
            parametros.add(filtro.estado());
        }
        if (filtro.texto() != null) {
            donde.append(" AND (asunto ILIKE ? ESCAPE '\\' OR descripcion ILIKE ? ESCAPE '\\'"
                    + " OR referencia_compra ILIKE ? ESCAPE '\\')");
            String patron = "%" + filtro.texto().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            parametros.addAll(List.of(patron, patron, patron));
        }
        if (filtro.desde() != null) {
            donde.append(" AND registrado_en >= ?");
            parametros.add(filtro.desde());
        }
        if (filtro.hasta() != null) {
            donde.append(" AND registrado_en < ?");
            parametros.add(filtro.hasta());
        }
        Long total = jdbc.queryForObject("SELECT count(*)" + donde, Long.class, parametros.toArray());
        List<Object> conPagina = new ArrayList<>(parametros);
        conPagina.add(filtro.tamanio());
        conPagina.add((long) filtro.pagina() * filtro.tamanio());
        List<Interaccion> interacciones = jdbc.query("SELECT " + COLUMNAS + donde
                + " ORDER BY registrado_en DESC, id DESC LIMIT ? OFFSET ?", FILA, conPagina.toArray());
        return new PaginaInteracciones(interacciones, total == null ? 0 : total, filtro.pagina(), filtro.tamanio());
    }

    private static CompraVinculada compra(ResultSet rs) throws SQLException {
        String referencia = rs.getString("compra_referencia");
        return referencia == null ? null : new CompraVinculada(referencia,
                rs.getObject("compra_fecha", OffsetDateTime.class), rs.getBigDecimal("compra_monto_total"),
                rs.getString("compra_estado"), rs.getString("compra_vinculada_por"),
                rs.getObject("compra_vinculada_en", OffsetDateTime.class));
    }

    /** Campos editables ya normalizados: sin espacios en los extremos y los opcionales vacíos como nulos. */
    public record Campos(String tipo, String canal, String asunto, String descripcion, String referenciaCompra) {
    }
}
