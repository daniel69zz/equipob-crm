package com.maxiconecta.crm.comportamiento.sugerencia;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * SCRUM-379: consolida los perfiles de preferencias de los compañeros de segmento. Por categoría,
 * cuántos clientes la compran, cuántas compras suman y cuánto gastaron; y cuántos compañeros tienen
 * perfil con al menos una categoría, que es la base de la afinidad.
 */
@Repository
public class AgregadoSegmento {
    private final JdbcTemplate jdbc;
    public AgregadoSegmento(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public ConsumoSegmento consumir(List<Long> clientes) {
        if (clientes.isEmpty()) { return new ConsumoSegmento(0, List.of()); }
        Long[] ids = clientes.toArray(Long[]::new);
        Integer conPreferencias = jdbc.query(conexion -> {
            var sentencia = conexion.prepareStatement("""
                    SELECT count(DISTINCT cliente_id) FROM comportamiento.preferencia_categoria
                    WHERE cliente_id = ANY (?)
                    """);
            sentencia.setArray(1, conexion.createArrayOf("bigint", ids));
            return sentencia;
        }, rs -> rs.next() ? rs.getInt(1) : 0);
        List<CategoriaSegmento> categorias = jdbc.query(conexion -> {
            var sentencia = conexion.prepareStatement("""
                    SELECT categoria, count(DISTINCT cliente_id) AS clientes, sum(compras) AS compras,
                           sum(monto) AS monto
                    FROM comportamiento.preferencia_categoria
                    WHERE cliente_id = ANY (?)
                    GROUP BY categoria
                    """);
            sentencia.setArray(1, conexion.createArrayOf("bigint", ids));
            return sentencia;
        }, (rs, fila) -> new CategoriaSegmento(rs.getString("categoria"), rs.getInt("clientes"),
                rs.getLong("compras"), rs.getBigDecimal("monto")));
        return new ConsumoSegmento(conPreferencias == null ? 0 : conPreferencias, categorias);
    }

    /** {@code clientesConPreferencias}: compañeros con perfil y al menos una categoría. */
    public record ConsumoSegmento(int clientesConPreferencias, List<CategoriaSegmento> categorias) {
        public ConsumoSegmento { categorias = List.copyOf(categorias); }
    }

    public record CategoriaSegmento(String categoria, int clientes, long compras, BigDecimal monto) { }
}
