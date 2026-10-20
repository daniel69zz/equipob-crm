package com.maxiconecta.crm.segmentacion.asignacion;

import com.maxiconecta.crm.segmentacion.criterio.Criterio;
import com.maxiconecta.crm.segmentacion.segmento.NivelConsumo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Segmento, métricas y eventos aplicados de cada cliente (SCRUM-264, SCRUM-241). Se usa dentro de la
 * transacción de {@link AsignacionSegmento}, con el cliente bloqueado.
 */
@Repository
public class RegistroSegmentos {

    private final JdbcTemplate jdbc;

    public RegistroSegmentos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Serializa los mensajes del mismo cliente hasta el fin de la transacción: dos compras del
     * cliente procesadas a la vez no pueden pisarse el segmento, aunque todavía no tenga fila.
     */
    public void bloquearCliente(long clienteId) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext('segmentacion.cliente'), ?::int)",
                Object.class, Long.hashCode(clienteId));
    }

    public boolean eventoYaProcesado(String idEvento) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM segmentacion.evento_procesado WHERE id_evento = ?)", Boolean.class,
                idEvento));
    }

    public void registrarEvento(String idEvento, long clienteId, String resultado) {
        jdbc.update("INSERT INTO segmentacion.evento_procesado (id_evento, cliente_id, resultado) VALUES (?, ?, ?)",
                idEvento, clienteId, resultado);
    }

    /** Momento del cálculo de la última foto de métricas aplicada al cliente. */
    public Optional<OffsetDateTime> metricasAplicadasEn(long clienteId) {
        return jdbc.query("SELECT calculadas_en FROM segmentacion.metricas_cliente WHERE cliente_id = ?",
                (fila, n) -> fila.getObject("calculadas_en", OffsetDateTime.class), clienteId).stream().findFirst();
    }

    /** Guarda la foto completa, con el nivel de consumo y la última compra del listado (SCRUM-277). */
    public void guardarMetricas(MetricasCliente metricas) {
        jdbc.update(conexion -> {
            var sentencia = conexion.prepareStatement("""
                    INSERT INTO segmentacion.metricas_cliente
                        (cliente_id, frecuencia, valor, categorias, calculadas_en, id_evento_origen,
                         clasificacion_frecuencia, nivel_consumo, ultima_compra)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (cliente_id) DO UPDATE SET frecuencia = EXCLUDED.frecuencia, valor = EXCLUDED.valor,
                        categorias = EXCLUDED.categorias, calculadas_en = EXCLUDED.calculadas_en,
                        id_evento_origen = EXCLUDED.id_evento_origen,
                        clasificacion_frecuencia = EXCLUDED.clasificacion_frecuencia,
                        nivel_consumo = EXCLUDED.nivel_consumo, ultima_compra = EXCLUDED.ultima_compra,
                        actualizadas_en = now()
                    """);
            Array categorias = conexion.createArrayOf("text", metricas.categoriasOVacio().toArray());
            NivelConsumo nivel = metricas.nivelConsumoConocido();
            sentencia.setLong(1, metricas.idCliente());
            sentencia.setLong(2, metricas.frecuencia());
            sentencia.setBigDecimal(3, metricas.valor());
            sentencia.setArray(4, categorias);
            sentencia.setTimestamp(5, Timestamp.from(metricas.calculadasEn().toInstant()));
            sentencia.setString(6, metricas.idEvento());
            sentencia.setString(7, metricas.clasificacionFrecuencia());
            sentencia.setString(8, nivel == null ? null : nivel.name());
            sentencia.setTimestamp(9, metricas.ultimaCompra() == null ? null
                    : Timestamp.from(metricas.ultimaCompra().toInstant()));
            return sentencia;
        });
    }

    public Optional<SegmentoActual> segmento(long clienteId) {
        return jdbc.query("""
                SELECT segmento, criterio_id, indicador, asignado_en FROM segmentacion.segmento_cliente
                WHERE cliente_id = ?
                """, (fila, n) -> new SegmentoActual(fila.getString("segmento"),
                (Long) fila.getObject("criterio_id"), fila.getString("indicador"),
                fila.getObject("asignado_en", OffsetDateTime.class)), clienteId).stream().findFirst();
    }

    public void asignar(long clienteId, Criterio criterio, String idEvento) {
        jdbc.update("""
                INSERT INTO segmentacion.segmento_cliente
                    (cliente_id, segmento, criterio_id, indicador, id_evento_origen, asignado_en)
                VALUES (?, ?, ?, ?, ?, now())
                ON CONFLICT (cliente_id) DO UPDATE SET segmento = EXCLUDED.segmento,
                    criterio_id = EXCLUDED.criterio_id, indicador = EXCLUDED.indicador,
                    id_evento_origen = EXCLUDED.id_evento_origen, asignado_en = now()
                """, clienteId, criterio.nombre(), criterio.id(), criterio.indicador().name(), idEvento);
    }

    public void retirar(long clienteId) {
        jdbc.update("DELETE FROM segmentacion.segmento_cliente WHERE cliente_id = ?", clienteId);
    }

    /** Segmento vigente; {@code criterioId} e {@code indicador} faltan en asignaciones previas a SCRUM-26. */
    public record SegmentoActual(String segmento, Long criterioId, String indicador, OffsetDateTime asignadoEn) {
    }
}
