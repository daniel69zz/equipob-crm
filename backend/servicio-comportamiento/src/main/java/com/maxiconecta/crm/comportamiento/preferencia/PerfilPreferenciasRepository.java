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
        jdbc.update("""
                INSERT INTO comportamiento.perfil_preferencias (cliente_id, construido_en, version, criterio)
                VALUES (?, ?, 1, ?)
                ON CONFLICT (cliente_id) DO UPDATE SET construido_en = EXCLUDED.construido_en,
                    version = comportamiento.perfil_preferencias.version + 1, criterio = EXCLUDED.criterio
                """, clienteId, fecha, PerfilPreferencias.CRITERIO);
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
                SELECT construido_en, version, criterio FROM comportamiento.perfil_preferencias WHERE cliente_id = ?
                """, (rs, fila) -> new Cabecera(rs.getObject("construido_en", OffsetDateTime.class),
                rs.getLong("version"), rs.getString("criterio")), clienteId);
        if (cabeceras.isEmpty()) { return PerfilPreferencias.pendiente(clienteId); }
        List<CategoriaPreferida> categorias = jdbc.query("""
                SELECT posicion, categoria, compras, unidades, monto FROM comportamiento.preferencia_categoria
                WHERE cliente_id = ? ORDER BY posicion
                """, (rs, fila) -> new CategoriaPreferida(rs.getInt("posicion"), rs.getString("categoria"),
                rs.getLong("compras"), rs.getLong("unidades"), rs.getBigDecimal("monto")), clienteId);
        Cabecera c = cabeceras.get(0);
        return new PerfilPreferencias(clienteId, true, categorias.isEmpty(), c.fecha(), c.version(), c.criterio(), categorias);
    }

    private record Cabecera(OffsetDateTime fecha, long version, String criterio) { }
}
