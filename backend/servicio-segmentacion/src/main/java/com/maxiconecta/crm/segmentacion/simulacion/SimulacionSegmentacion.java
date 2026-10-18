package com.maxiconecta.crm.segmentacion.simulacion;

import com.maxiconecta.crm.segmentacion.asignacion.ReglaPrioridadSegmento;
import com.maxiconecta.crm.segmentacion.criterio.Criterio;
import com.maxiconecta.crm.segmentacion.criterio.CriterioRepository;
import com.maxiconecta.crm.segmentacion.criterio.CriterioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** SCRUM-284/287: instantánea de solo lectura, sin llamadas al asignador ni a auditorías de negocio. */
@Service
public class SimulacionSegmentacion {
    private static final Logger log = LoggerFactory.getLogger(SimulacionSegmentacion.class);
    private final JdbcTemplate jdbc;
    private final CriterioService validacion;
    private final CriterioRepository criterios;
    private final int maxClientes;
    private final int maxSegundos;

    public SimulacionSegmentacion(JdbcTemplate jdbc, CriterioService validacion, CriterioRepository criterios,
            @Value("${segmentacion.simulacion.max-clientes:100000}") int maxClientes,
            @Value("${segmentacion.simulacion.max-segundos:10}") int maxSegundos) {
        if (maxClientes < 1 || maxClientes > 1000000 || maxSegundos < 1 || maxSegundos > 60)
            throw new IllegalArgumentException("Límites de simulación inválidos");
        this.jdbc = jdbc; this.validacion = validacion; this.criterios = criterios;
        this.maxClientes = maxClientes; this.maxSegundos = maxSegundos;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 65)
    public ResultadoSimulacion simular(SolicitudSimulacion solicitud) {
        long inicio = System.nanoTime();
        try {
            Criterio propuesta = validacion.prepararSimulacion(solicitud.criterioId(), solicitud.regla());
            List<Criterio> escenario = new ArrayList<>(criterios.activos());
            comprobarTiempo(inicio);
            escenario.removeIf(c -> c.id().equals(propuesta.id()));
            if (propuesta.activo()) escenario.add(propuesta);
            Map<String, Fila> filas = new LinkedHashMap<>();
            for (var c : escenario) filas.put(clave(c.id(), c.nombre()), new Fila(c.nombre(), c.id().equals(propuesta.id())));
            filas.putIfAbsent(clave(propuesta.id(), propuesta.nombre()), new Fila(propuesta.nombre(), true));
            filas.put("sin-segmento", new Fila("Sin segmento", false));
            long[] totales = {0, 0};
            OffsetDateTime[] fechas = {null, null};
            // Límite de filas y tiempo por sentencia; no se carga la población completa en memoria.
            jdbc.query(conexion -> {
                var ps = conexion.prepareStatement("""
                        SELECT m.frecuencia, m.valor, m.categorias, m.calculadas_en,
                               s.criterio_id, s.segmento
                        FROM segmentacion.metricas_cliente m
                        LEFT JOIN segmentacion.segmento_cliente s ON s.cliente_id=m.cliente_id
                        ORDER BY m.cliente_id LIMIT ?
                        """);
                ps.setInt(1, maxClientes + 1); ps.setFetchSize(500); ps.setQueryTimeout(maxSegundos);
                return ps;
            }, (RowCallbackHandler) rs -> {
                if (++totales[0] > maxClientes) throw noDisponible("La población supera el límite de " + maxClientes + " clientes por simulación.");
                comprobarTiempo(inicio);
                var valor = rs.getBigDecimal("valor");
                long frecuencia = rs.getLong("frecuencia");
                boolean sinFrecuencia = rs.wasNull();
                var arreglo = rs.getArray("categorias");
                if (sinFrecuencia || frecuencia < 0 || valor == null || valor.signum() < 0 || arreglo == null)
                    throw datosInconsistentes("Las métricas contienen datos inconsistentes; revise su origen.");
                List<String> categorias;
                try { categorias = Arrays.asList((String[]) arreglo.getArray()); } finally { arreglo.free(); }
                if (categorias.stream().anyMatch(c -> c == null || c.isBlank()))
                    throw datosInconsistentes("Las métricas contienen categorías inválidas.");
                var fecha = rs.getObject("calculadas_en", OffsetDateTime.class);
                if (fechas[0] == null || fecha.isBefore(fechas[0])) fechas[0] = fecha;
                if (fechas[1] == null || fecha.isAfter(fechas[1])) fechas[1] = fecha;
                String nombreActual = rs.getString("segmento");
                String actual = clave((Long) rs.getObject("criterio_id"), nombreActual);
                filas.computeIfAbsent(actual, k -> new Fila(nombreActual == null ? "Sin segmento" : nombreActual, false)).actual++;
                var elegido = ReglaPrioridadSegmento.elegir(escenario, frecuencia, valor, categorias);
                String simulado = elegido.map(c -> clave(c.id(), c.nombre())).orElse("sin-segmento");
                filas.get(simulado).simulado++;
                if (!actual.equals(simulado)) totales[1]++;
            });
            Long excluidos = jdbc.query(conexion -> {
                var ps = conexion.prepareStatement("""
                        SELECT count(*) FROM segmentacion.segmento_cliente s
                        WHERE NOT EXISTS (SELECT 1 FROM segmentacion.metricas_cliente m WHERE m.cliente_id=s.cliente_id)
                        """);
                ps.setQueryTimeout(maxSegundos); return ps;
            }, rs -> { rs.next(); return rs.getLong(1); });
            comprobarTiempo(inicio);
            var distribucion = filas.entrySet().stream().map(e -> new ResultadoSimulacion.Distribucion(
                    e.getKey(), e.getValue().nombre, e.getValue().propuesta, e.getValue().actual,
                    e.getValue().simulado, e.getValue().simulado - e.getValue().actual)).toList();
            long duracion = (System.nanoTime() - inicio) / 1000000;
            log.info("Simulación completada: clientes={}, cambios={}, duraciónMs={}", totales[0], totales[1], duracion);
            return new ResultadoSimulacion(totales[0], excluidos, totales[1], propuesta.activo(),
                    OffsetDateTime.now(ZoneOffset.UTC), fechas[0], fechas[1], duracion, distribucion);
        } catch (DataAccessException ex) {
            log.warn("Simulación no disponible: {}", ex.getClass().getSimpleName());
            throw noDisponible("No se pudo completar la lectura de datos dentro del tiempo disponible. Intente nuevamente.");
        }
    }

    private void comprobarTiempo(long inicio) {
        if (System.nanoTime() - inicio > maxSegundos * 1000000000L)
            throw noDisponible("La simulación excedió el tiempo disponible. Intente nuevamente.");
    }

    private static ResponseStatusException noDisponible(String mensaje) {
        log.warn("Simulación interrumpida: {}", mensaje);
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, mensaje);
    }

    private static ResponseStatusException datosInconsistentes(String mensaje) {
        log.warn("Simulación interrumpida: {}", mensaje);
        return new ResponseStatusException(HttpStatus.CONFLICT, mensaje);
    }

    private static String clave(Long criterioId, String nombre) {
        return criterioId != null ? "criterio:" + criterioId : nombre == null ? "sin-segmento" : "legado:" + nombre;
    }

    private static class Fila {
        final String nombre; final boolean propuesta; long actual; long simulado;
        Fila(String nombre, boolean propuesta) { this.nombre = nombre; this.propuesta = propuesta; }
    }
}
