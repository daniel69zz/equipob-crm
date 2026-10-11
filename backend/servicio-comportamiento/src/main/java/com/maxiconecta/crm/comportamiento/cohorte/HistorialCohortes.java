package com.maxiconecta.crm.comportamiento.cohorte;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/**
 * Prepara el historial de compras para el análisis de cohortes (SCRUM-328,
 * docs/compra/retencion-cohortes.md): agrega en la base, por cohorte y mes, cuántos clientes
 * distintos tienen al menos una compra vigente en ese mes. Solo cuentan las compras vigentes (una
 * anulada no es primera compra ni retención) con fecha hasta {@code hasta}; los meses se cuentan en
 * {@code zona}. La cohorte se calcula sobre todo el historial y después se filtra por el rango.
 */
@Repository
public class HistorialCohortes {

    private static final String CONSULTA = """
            WITH meses AS (
                SELECT id_cliente_origen,
                       date_trunc('month', fecha AT TIME ZONE :zona) AS mes
                FROM comportamiento.compra
                WHERE monto_total - monto_revertido > 0
                  AND fecha <= :hasta
                GROUP BY 1, 2
            ), cohortes AS (
                SELECT id_cliente_origen, MIN(mes) AS cohorte
                FROM meses
                GROUP BY id_cliente_origen
            )
            SELECT to_char(c.cohorte, 'YYYY-MM') AS cohorte,
                   to_char(m.mes, 'YYYY-MM') AS mes,
                   COUNT(*) AS clientes
            FROM meses m
            JOIN cohortes c ON c.id_cliente_origen = m.id_cliente_origen
            WHERE c.cohorte >= CAST(:cohorteDesde AS timestamp)
              AND c.cohorte <= CAST(:cohorteHasta AS timestamp)
            GROUP BY c.cohorte, m.mes
            ORDER BY c.cohorte, m.mes
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public HistorialCohortes(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ActividadCohorte> actividad(YearMonth cohorteDesde, YearMonth cohorteHasta, OffsetDateTime hasta,
                                            ZoneId zona) {
        MapSqlParameterSource parametros = new MapSqlParameterSource()
                .addValue("zona", zona.getId())
                .addValue("hasta", hasta)
                .addValue("cohorteDesde", cohorteDesde.atDay(1).toString())
                .addValue("cohorteHasta", cohorteHasta.atDay(1).toString());
        return jdbc.query(CONSULTA, parametros, (fila, n) -> new ActividadCohorte(
                YearMonth.parse(fila.getString("cohorte")), YearMonth.parse(fila.getString("mes")),
                fila.getLong("clientes")));
    }
}
