package com.maxiconecta.crm.segmentacion.consulta;

import com.maxiconecta.crm.segmentacion.consulta.OrdenClientes.Direccion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Lee los clientes de un segmento o nivel de consumo (SCRUM-277) de lo que ya guarda la
 * segmentación: el segmento vigente y la última foto de métricas de cada cliente. Filtra, ordena y
 * pagina en la base (docs/segmentacion/criterio-consulta-clientes.md).
 */
@Repository
public class ListadoClientesRepository {

    /**
     * Un cliente puede tener métricas sin segmento (no cumple ningún criterio) o, en asignaciones
     * anteriores a SCRUM-26, segmento sin métricas: por eso se cruzan con FULL JOIN.
     */
    private static final String CLIENTES = """
            SELECT COALESCE(s.cliente_id, m.cliente_id) AS cliente_id, s.segmento, s.indicador, s.asignado_en,
                   m.nivel_consumo, m.valor, m.frecuencia, m.ultima_compra
            FROM segmentacion.segmento_cliente s
            FULL JOIN segmentacion.metricas_cliente m ON m.cliente_id = s.cliente_id
            """;

    private static final RowMapper<FilaCliente> FILA = (r, n) -> new FilaCliente(r.getLong("cliente_id"),
            r.getString("segmento"), r.getString("indicador"), r.getObject("asignado_en", OffsetDateTime.class),
            r.getString("nivel_consumo"), r.getBigDecimal("valor"), (Long) r.getObject("frecuencia"),
            r.getObject("ultima_compra", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public ListadoClientesRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PaginaFilas buscar(FiltroClientes filtro, OrdenClientes orden, Direccion direccion, int pagina, int tamanio) {
        ZoneId zona = ZoneId.systemDefault();
        List<String> condiciones = new ArrayList<>();
        List<Object> valores = new ArrayList<>();
        if (filtro.segmento() != null) {
            condiciones.add("segmento = ?");
            valores.add(filtro.segmento());
        }
        if (filtro.nivelConsumo() != null) {
            condiciones.add("nivel_consumo = ?");
            valores.add(filtro.nivelConsumo().name());
        }
        if (filtro.ultimaCompraDesde() != null) {
            condiciones.add("ultima_compra >= ?");
            valores.add(filtro.ultimaCompraDesdeInclusive(zona));
        }
        if (filtro.ultimaCompraHasta() != null) {
            condiciones.add("ultima_compra < ?");
            valores.add(filtro.ultimaCompraHastaExclusive(zona));
        }
        // FiltroClientes exige un segmento o un nivel: siempre hay al menos una condición.
        String desde = " FROM (" + CLIENTES + ") clientes WHERE " + String.join(" AND ", condiciones);

        Long total = jdbc.queryForObject("SELECT count(*)" + desde, Long.class, valores.toArray());
        List<Object> conPagina = new ArrayList<>(valores);
        conPagina.add(tamanio);
        conPagina.add((long) pagina * tamanio);
        List<FilaCliente> filas = jdbc.query("SELECT *" + desde + " ORDER BY " + orden.ordenSql(direccion)
                + " LIMIT ? OFFSET ?", FILA, conPagina.toArray());
        return new PaginaFilas(filas, total == null ? 0 : total);
    }

    /**
     * Segmentos que se pueden consultar: los de criterios activos, aunque todavía no tengan clientes
     * (criterio de aceptación 3), y los que algún cliente conserva, aunque su criterio ya no esté
     * activo o haya cambiado de nombre. Ordenados por nombre.
     */
    public List<SegmentoDisponible> segmentos() {
        return jdbc.query("""
                SELECT COALESCE(c.nombre, a.segmento) AS segmento, COALESCE(c.indicador, a.indicador) AS indicador,
                       c.activo AS criterio_activo, COALESCE(a.clientes, 0) AS clientes
                FROM (SELECT segmento, max(indicador) AS indicador, count(*) AS clientes
                      FROM segmentacion.segmento_cliente GROUP BY segmento) a
                FULL JOIN segmentacion.criterio c ON c.nombre = a.segmento
                WHERE c.activo OR a.clientes > 0
                ORDER BY lower(COALESCE(c.nombre, a.segmento))
                """, (r, n) -> new SegmentoDisponible(r.getString("segmento"), r.getString("indicador"),
                (Boolean) r.getObject("criterio_activo"), r.getLong("clientes")));
    }

    /** Cliente de la página tal como está en la segmentación, todavía sin su nombre. */
    public record FilaCliente(long clienteId, String segmento, String indicador, OffsetDateTime asignadoEn,
                              String nivelConsumo, BigDecimal valor, Long frecuencia, OffsetDateTime ultimaCompra) {
    }

    public record PaginaFilas(List<FilaCliente> filas, long total) {
    }

    /**
     * Segmento del catálogo. {@code criterioActivo} es {@code null} cuando ningún criterio tiene hoy
     * ese nombre (asignaciones anteriores a un cambio de nombre o a SCRUM-26).
     */
    public record SegmentoDisponible(String segmento, String indicador, Boolean criterioActivo, long clientes) {
    }
}
