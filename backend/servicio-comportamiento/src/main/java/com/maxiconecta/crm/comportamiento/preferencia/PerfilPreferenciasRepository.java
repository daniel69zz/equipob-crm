package com.maxiconecta.crm.comportamiento.preferencia;

import com.maxiconecta.crm.comportamiento.preferencia.PerfilPreferencias.CategoriaPreferida;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/** Cabecera y categorías se reemplazan en la misma transacción de PerfilesPreferencias. */
@Repository
public class PerfilPreferenciasRepository {
    private final JdbcTemplate jdbc;
    public PerfilPreferenciasRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void bloquear(long clienteId) {
        // El prefijo separa este bloqueo de los de otros dominios; existe aun sin cabecera previa.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", "preferencias:" + clienteId);
    }

    public PerfilPreferencias guardar(long clienteId, OffsetDateTime fecha, List<CategoriaPreferida> categorias) {
        return guardar(clienteId, fecha, categorias, PerfilPreferencias.CONSTRUCCION_MANUAL, null);
    }

    /** Reemplaza el perfil y anota qué lo originó: la construcción manual o un evento de venta (SCRUM-369). */
    public PerfilPreferencias guardar(long clienteId, OffsetDateTime fecha, List<CategoriaPreferida> categorias,
                                      String origen, String idEventoOrigen) {
        jdbc.update("""
                INSERT INTO comportamiento.perfil_preferencias
                    (cliente_id, construido_en, version, criterio, origen, id_evento_origen)
                VALUES (?, ?, 1, ?, ?, ?)
                ON CONFLICT (cliente_id) DO UPDATE SET construido_en = EXCLUDED.construido_en,
                    version = comportamiento.perfil_preferencias.version + 1, criterio = EXCLUDED.criterio,
                    origen = EXCLUDED.origen, id_evento_origen = EXCLUDED.id_evento_origen
                """, clienteId, fecha, PerfilPreferencias.CRITERIO, origen, idEventoOrigen);
        jdbc.update("DELETE FROM comportamiento.preferencia_categoria WHERE cliente_id = ?", clienteId);
        jdbc.batchUpdate("""
                INSERT INTO comportamiento.preferencia_categoria
                    (cliente_id, categoria, posicion, compras, unidades, monto) VALUES (?, ?, ?, ?, ?, ?)
                """, categorias, 100, (ps, c) -> {
            ps.setLong(1, clienteId);
            ps.setString(2, c.categoria());
            ps.setInt(3, c.posicion());
            ps.setLong(4, c.compras());
            ps.setLong(5, c.unidades());
            ps.setBigDecimal(6, c.monto());
        });
        return obtener(clienteId);
    }

    public PerfilPreferencias obtener(long clienteId) {
        var cabeceras = jdbc.query("""
                SELECT construido_en, version, criterio, origen, id_evento_origen
                FROM comportamiento.perfil_preferencias WHERE cliente_id = ?
                """, (rs, fila) -> new Cabecera(rs.getObject("construido_en", OffsetDateTime.class),
                rs.getLong("version"), rs.getString("criterio"), rs.getString("origen"),
                rs.getString("id_evento_origen")), clienteId);
        if (cabeceras.isEmpty()) { return PerfilPreferencias.pendiente(clienteId); }
        List<CategoriaPreferida> categorias = jdbc.query("""
                SELECT posicion, categoria, compras, unidades, monto FROM comportamiento.preferencia_categoria
                WHERE cliente_id = ? ORDER BY posicion
                """, (rs, fila) -> new CategoriaPreferida(rs.getInt("posicion"), rs.getString("categoria"),
                rs.getLong("compras"), rs.getLong("unidades"), rs.getBigDecimal("monto")), clienteId);
        Cabecera c = cabeceras.get(0);
        return new PerfilPreferencias(clienteId, true, categorias.isEmpty(), c.fecha(), c.version(), c.criterio(),
                categorias, c.origen(), c.idEventoOrigen());
    }

    /** SCRUM-42: el evento de venta ya actualizó el perfil (un reenvío no lo actualiza dos veces). */
    public boolean eventoAplicado(String idEvento) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM comportamiento.actualizacion_preferencias WHERE id_evento = ?)",
                Boolean.class, idEvento));
    }

    /** SCRUM-368: trazabilidad de una actualización por evento, en la misma transacción que el perfil. */
    public void registrarActualizacion(String idEvento, String tipoEvento, PerfilPreferencias anterior,
                                       PerfilPreferencias nuevo) {
        jdbc.update(conexion -> {
            var sentencia = conexion.prepareStatement("""
                    INSERT INTO comportamiento.actualizacion_preferencias
                        (id_evento, cliente_id, tipo_evento_origen, version_anterior, version_nueva,
                         categorias_anteriores, categorias_nuevas, actualizado_en)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """);
            sentencia.setString(1, idEvento);
            sentencia.setLong(2, nuevo.clienteId());
            sentencia.setString(3, tipoEvento);
            sentencia.setLong(4, anterior.version());
            sentencia.setLong(5, nuevo.version());
            sentencia.setArray(6, conexion.createArrayOf("text", nombres(anterior)));
            sentencia.setArray(7, conexion.createArrayOf("text", nombres(nuevo)));
            sentencia.setObject(8, nuevo.construidoEn());
            return sentencia;
        });
    }

    public List<Actualizacion> actualizaciones(long clienteId) {
        return jdbc.query("""
                SELECT id_evento, tipo_evento_origen, version_anterior, version_nueva, categorias_anteriores,
                       categorias_nuevas, actualizado_en
                FROM comportamiento.actualizacion_preferencias WHERE cliente_id = ?
                ORDER BY actualizado_en DESC, version_nueva DESC
                """, (rs, fila) -> new Actualizacion(rs.getString("id_evento"), rs.getString("tipo_evento_origen"),
                rs.getLong("version_anterior"), rs.getLong("version_nueva"),
                List.of((String[]) rs.getArray("categorias_anteriores").getArray()),
                List.of((String[]) rs.getArray("categorias_nuevas").getArray()),
                rs.getObject("actualizado_en", OffsetDateTime.class)), clienteId);
    }

    private static Object[] nombres(PerfilPreferencias perfil) {
        return perfil.categorias().stream().map(CategoriaPreferida::categoria).toArray();
    }

    /** Una actualización del perfil por evento: cuándo, qué evento y cómo cambiaron las categorías. */
    public record Actualizacion(String idEvento, String tipoEventoOrigen, long versionAnterior, long versionNueva,
                                List<String> categoriasAnteriores, List<String> categoriasNuevas,
                                OffsetDateTime actualizadoEn) { }

    private record Cabecera(OffsetDateTime fecha, long version, String criterio, String origen,
                            String idEventoOrigen) { }
}
