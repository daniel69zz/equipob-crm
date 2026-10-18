package com.maxiconecta.crm.segmentacion.consulta;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.List;
import static org.springframework.http.HttpStatus.*;

/**
 * SCRUM-277/273. Lectura entre esquemas de la base centralizada, sin escribir en otros dominios.
 * La identidad proviene del vínculo actual de Perfil, no de la copia de id_cliente en compras.
 */
@Repository
public class RepositorioConsultaClientes {
    private final NamedParameterJdbcTemplate jdbc;
    public RepositorioConsultaClientes(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<SegmentoDisponible> catalogo() {
        return jdbc.query("""
                SELECT id, nombre, activo, false AS legado FROM segmentacion.criterio
                UNION ALL
                SELECT NULL::bigint, segmento, true, true FROM segmentacion.segmento_cliente
                WHERE criterio_id IS NULL GROUP BY segmento
                ORDER BY nombre, id NULLS LAST
                """, (rs, n) -> new SegmentoDisponible((Long) rs.getObject("id"),
                rs.getString("nombre"), rs.getBoolean("activo"), rs.getBoolean("legado")));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Pagina buscar(FiltroConsultaClientes filtro, UmbralesConsumo.Valores umbrales) {
        var parametros = new MapSqlParameterSource()
                .addValue("medio", umbrales.medio()).addValue("alto", umbrales.alto())
                .addValue("limite", filtro.tamanio()).addValue("offset", (long) filtro.pagina() * filtro.tamanio());
        String condiciones = " WHERE p.id_cliente_consolidado IS NULL ";
        if (filtro.segmentoId() != null) {
            parametros.addValue("segmentoId", filtro.segmentoId());
            if (jdbc.queryForObject("SELECT count(*) FROM segmentacion.criterio WHERE id = :segmentoId",
                    parametros, Long.class) == 0) throw new ResponseStatusException(NOT_FOUND, "El segmento no existe");
            condiciones += " AND sc.criterio_id = :segmentoId ";
        }
        if (!filtro.segmento().isEmpty()) {
            parametros.addValue("segmento", filtro.segmento());
            if (jdbc.queryForObject("""
                    SELECT count(*) FROM segmentacion.segmento_cliente
                    WHERE criterio_id IS NULL AND segmento = :segmento
                    """, parametros, Long.class) == 0)
                throw new ResponseStatusException(NOT_FOUND, "El segmento anterior no existe");
            condiciones += " AND sc.criterio_id IS NULL AND sc.segmento = :segmento ";
        }
        if (!filtro.nombre().isEmpty()) {
            parametros.addValue("nombre", filtro.nombre());
            condiciones += " AND strpos(lower(concat_ws(' ', p.nombres, p.apellidos)), lower(:nombre)) > 0 ";
        }
        String base = """
                WITH candidatos AS (
                    SELECT p.id AS cliente_id,
                           COALESCE(NULLIF(btrim(concat_ws(' ', p.nombres, p.apellidos)), ''), 'Sin nombre') AS nombre,
                           sc.criterio_id AS segmento_id, COALESCE(c.nombre, sc.segmento) AS segmento
                    FROM perfil.cliente p
                    LEFT JOIN segmentacion.segmento_cliente sc ON sc.cliente_id = p.id
                    LEFT JOIN segmentacion.criterio c ON c.id = sc.criterio_id
                """ + condiciones + """
                ), consumo AS (
                    SELECT o.id_cliente, SUM(b.monto_total - b.monto_revertido) AS valor,
                           MAX(b.fecha) AS ultima_compra
                    FROM candidatos p
                    JOIN perfil.cliente_origen o ON o.id_cliente = p.cliente_id
                    JOIN comportamiento.compra b ON b.id_cliente_origen = o.id_cliente_origen
                    WHERE b.monto_total - b.monto_revertido > 0
                    GROUP BY o.id_cliente
                ), listado AS (
                    SELECT p.*, co.ultima_compra,
                        CASE WHEN co.valor >= :alto THEN 'ALTO'
                             WHEN co.valor >= :medio THEN 'MEDIO'
                             WHEN co.valor > 0 THEN 'BAJO' ELSE NULL END AS nivel_consumo
                    FROM candidatos p LEFT JOIN consumo co ON co.id_cliente = p.cliente_id
                )
                """;
        String donde = "";
        if (!filtro.nivelConsumo().isEmpty()) {
            if (filtro.nivelConsumo().equals("SIN_NIVEL")) donde = " WHERE nivel_consumo IS NULL ";
            else {
                parametros.addValue("nivel", filtro.nivelConsumo());
                donde = " WHERE nivel_consumo = :nivel ";
            }
        }
        String columna = switch (filtro.orden()) {
            case "segmento" -> "lower(segmento)";
            case "nivelConsumo" -> "CASE nivel_consumo WHEN 'BAJO' THEN 1 WHEN 'MEDIO' THEN 2 WHEN 'ALTO' THEN 3 END";
            case "ultimaCompra" -> "ultima_compra";
            default -> "lower(nombre)";
        };
        long total = jdbc.queryForObject(base + "SELECT count(*) FROM listado" + donde, parametros, Long.class);
        List<Cliente> clientes = jdbc.query(base + "SELECT * FROM listado" + donde
                        + " ORDER BY " + columna + " " + filtro.direccion()
                        + " NULLS LAST, cliente_id ASC LIMIT :limite OFFSET :offset",
                parametros, (rs, n) -> new Cliente(rs.getLong("cliente_id"), rs.getString("nombre"),
                        (Long) rs.getObject("segmento_id"), rs.getString("segmento"),
                        rs.getString("nivel_consumo"), rs.getObject("ultima_compra", OffsetDateTime.class)));
        return new Pagina(clientes, filtro.pagina(), filtro.tamanio(), total);
    }

    public record SegmentoDisponible(Long id, String nombre, boolean activo, boolean legado) { }
    public record Cliente(long clienteId, String nombre, Long segmentoId, String segmento,
                          String nivelConsumo, OffsetDateTime ultimaCompra) { }
    public record Pagina(List<Cliente> clientes, int pagina, int tamanio, long total) { }
}
