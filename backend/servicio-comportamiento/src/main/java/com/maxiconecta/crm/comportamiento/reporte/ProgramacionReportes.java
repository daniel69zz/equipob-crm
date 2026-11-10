package com.maxiconecta.crm.comportamiento.reporte;

import com.maxiconecta.crm.comportamiento.periodo.RangoPeriodo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.*;
import java.util.*;

/** Programaciones privadas. El bloqueo de fila serializa ejecución, edición y cancelación. */
@Service
public class ProgramacionReportes {
    static final ZoneId ZONA = ZoneId.of("America/La_Paz");
    private final JdbcTemplate jdbc;
    private final ExportacionReportes exportacion;
    private final Clock reloj;
    private final TransactionTemplate tx;
    private final TransactionTemplate generacion;

    public ProgramacionReportes(JdbcTemplate jdbc, ExportacionReportes exportacion, Clock reloj,
                               PlatformTransactionManager gestor) {
        this.jdbc = jdbc;
        this.exportacion = exportacion;
        this.reloj = reloj;
        tx = new TransactionTemplate(gestor);
        generacion = new TransactionTemplate(gestor);
        generacion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record Programacion(UUID id, String nombre, SolicitudProgramacion.Frecuencia frecuencia,
                               FormatoExportacion formato, int dias, String segmento, OffsetDateTime inicio,
                               OffsetDateTime fin, OffsetDateTime proxima, String estado) {}
    public record Ejecucion(UUID id, UUID programacion, String reporte, OffsetDateTime prevista,
                            OffsetDateTime ejecutada, String estado, String causa, String nombre, boolean leida) {}

    private Programacion fila(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Programacion(rs.getObject("id", UUID.class), rs.getString("nombre"),
                SolicitudProgramacion.Frecuencia.valueOf(rs.getString("frecuencia")),
                FormatoExportacion.valueOf(rs.getString("formato")), rs.getInt("dias"), rs.getString("segmento"),
                rs.getObject("inicio", OffsetDateTime.class), rs.getObject("fin", OffsetDateTime.class),
                rs.getObject("proxima", OffsetDateTime.class), rs.getString("estado"));
    }

    public List<Programacion> listar(String usuario) {
        return jdbc.query("SELECT * FROM comportamiento.reporte_programado WHERE usuario=? ORDER BY proxima,id",
                (rs, n) -> fila(rs), usuario);
    }

    public UUID guardar(String usuario, UUID id, SolicitudProgramacion s) {
        if (!s.inicio().isAfter(OffsetDateTime.now(reloj)) || (s.fin() != null && s.fin().isBefore(s.inicio()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El inicio debe ser futuro y el fin no puede precederlo");
        }
        UUID clave = id == null ? UUID.randomUUID() : id;
        tx.executeWithoutResult(t -> {
            if (id != null) {
                Programacion actual = obtener(usuario, id, true);
                if (actual.estado().equals("CANCELADA")) conflicto();
                jdbc.update("""
                    UPDATE comportamiento.reporte_programado SET nombre=?,frecuencia=?,formato=?,dias=?,segmento=?,
                    inicio=?,fin=?,proxima=? WHERE id=? AND usuario=?
                    """, s.nombre().strip(), s.frecuencia().name(), s.formato().name(), s.dias(), s.segmento(),
                        s.inicio(), s.fin(), s.inicio(), id, usuario);
            } else {
                jdbc.update("""
                    INSERT INTO comportamiento.reporte_programado
                    (id,usuario,nombre,frecuencia,formato,dias,segmento,inicio,fin,proxima,estado)
                    VALUES (?,?,?,?,?,?,?,?,?,?,'ACTIVA')
                    """, clave, usuario, s.nombre().strip(), s.frecuencia().name(), s.formato().name(), s.dias(),
                        s.segmento(), s.inicio(), s.fin(), s.inicio());
            }
        });
        return clave;
    }

    private Programacion obtener(String usuario, UUID id, boolean bloquear) {
        return jdbc.query("SELECT * FROM comportamiento.reporte_programado WHERE id=? AND usuario=?"
                        + (bloquear ? " FOR UPDATE" : ""), (rs, n) -> fila(rs), id, usuario).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programación inexistente"));
    }

    public void estado(String usuario, UUID id, String estado) {
        if (!Set.of("ACTIVA", "PAUSADA", "CANCELADA").contains(estado)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado inválido");
        }
        tx.executeWithoutResult(t -> {
            Programacion p = obtener(usuario, id, true);
            if (p.estado().equals("CANCELADA")) conflicto();
            OffsetDateTime proxima = p.proxima();
            if (estado.equals("ACTIVA")) {
                while (proxima.isBefore(OffsetDateTime.now(reloj))) proxima = siguiente(p, proxima);
                if (p.fin() != null && proxima.isAfter(p.fin())) conflicto();
            }
            jdbc.update("UPDATE comportamiento.reporte_programado SET estado=?,proxima=? WHERE id=?",
                    estado, proxima, id);
        });
    }

    private void conflicto() {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "La programación está cancelada o su vigencia terminó");
    }

    static OffsetDateTime siguiente(Programacion p, OffsetDateTime fecha) {
        ZonedDateTime local = fecha.atZoneSameInstant(ZONA);
        return switch (p.frecuencia()) {
            case DIARIA -> local.plusDays(1).toOffsetDateTime();
            case SEMANAL -> local.plusWeeks(1).toOffsetDateTime();
            case MENSUAL -> local.plusMonths(1).withDayOfMonth(Math.min(
                    p.inicio().atZoneSameInstant(ZONA).getDayOfMonth(), local.plusMonths(1).toLocalDate().lengthOfMonth()))
                    .toOffsetDateTime();
        };
    }

    /** Un lote acotado; SKIP LOCKED evita duplicados entre instancias. Se procesa una vez tras una caída. */
    public void ejecutarPendientes() {
        List<UUID> ids = jdbc.query("""
                SELECT id FROM comportamiento.reporte_programado
                WHERE estado='ACTIVA' AND proxima<=? ORDER BY proxima LIMIT 50
                """, (rs, n) -> rs.getObject(1, UUID.class), OffsetDateTime.now(reloj));
        for (UUID id : ids) ejecutar(id);
    }

    void ejecutar(UUID id) {
        tx.executeWithoutResult(t -> {
            OffsetDateTime ahora = OffsetDateTime.now(reloj);
            var filas = jdbc.query("""
                    SELECT * FROM comportamiento.reporte_programado WHERE id=? AND estado='ACTIVA'
                    AND proxima<=? FOR UPDATE SKIP LOCKED
                    """, (rs, n) -> fila(rs), id, ahora);
            if (filas.isEmpty()) return;
            Programacion p = filas.get(0);
            if (p.fin() != null && p.proxima().isAfter(p.fin())) {
                jdbc.update("UPDATE comportamiento.reporte_programado SET estado='PAUSADA' WHERE id=?", id);
                return;
            }
            String usuario = jdbc.queryForObject("SELECT usuario FROM comportamiento.reporte_programado WHERE id=?", String.class, id);
            ArchivoExportado archivo = null;
            String causa = null;
            try {
                LocalDate hasta = p.proxima().atZoneSameInstant(ZONA).toLocalDate().minusDays(1);
                archivo = generacion.execute(g -> exportacion.comportamiento(
                        new RangoPeriodo(hasta.minusDays(p.dias() - 1L), hasta), p.segmento(), p.formato(), usuario));
            } catch (RuntimeException ex) {
                causa = ex.getClass().getSimpleName() + ": " + Objects.toString(ex.getMessage(), "Sin detalle");
                causa = causa.substring(0, Math.min(500, causa.length()));
            }
            jdbc.update("""
                    INSERT INTO comportamiento.ejecucion_reporte
                    (id,programacion,prevista,ejecutada,estado,causa,nombre,formato,contenido) VALUES (?,?,?,?,?,?,?,?,?)
                    """, UUID.randomUUID(), id, p.proxima(), ahora, archivo == null ? "FALLIDA" : "COMPLETADA", causa,
                    archivo == null ? null : archivo.nombre(), p.formato().name(), archivo == null ? null : archivo.contenido());
            OffsetDateTime proxima = siguiente(p, p.proxima());
            while (!proxima.isAfter(ahora)) proxima = siguiente(p, proxima);
            jdbc.update("UPDATE comportamiento.reporte_programado SET proxima=?,estado=? WHERE id=?", proxima,
                    p.fin() != null && proxima.isAfter(p.fin()) ? "PAUSADA" : "ACTIVA", id);
        });
    }

    public List<Ejecucion> historial(String usuario, int pagina) {
        if (pagina < 0 || pagina > 1000000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página inválida");
        return jdbc.query("""
                SELECT e.*,p.nombre AS reporte FROM comportamiento.ejecucion_reporte e
                JOIN comportamiento.reporte_programado p ON p.id=e.programacion WHERE p.usuario=?
                ORDER BY e.ejecutada DESC,e.id LIMIT 50 OFFSET ?
                """, (rs, n) -> new Ejecucion(rs.getObject("id", UUID.class), rs.getObject("programacion", UUID.class),
                rs.getString("reporte"), rs.getObject("prevista", OffsetDateTime.class),
                rs.getObject("ejecutada", OffsetDateTime.class), rs.getString("estado"), rs.getString("causa"),
                rs.getString("nombre"), rs.getBoolean("leida")), usuario, pagina * 50);
    }

    public long noLeidas(String usuario) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM comportamiento.ejecucion_reporte e
                JOIN comportamiento.reporte_programado p ON p.id=e.programacion WHERE p.usuario=? AND NOT e.leida
                """, Long.class, usuario);
    }

    public ArchivoExportado descargar(String usuario, UUID id) {
        return jdbc.query("""
                SELECT e.* FROM comportamiento.ejecucion_reporte e JOIN comportamiento.reporte_programado p
                ON p.id=e.programacion WHERE e.id=? AND p.usuario=? AND e.estado='COMPLETADA'
                """, (rs, n) -> new ArchivoExportado(rs.getString("nombre"),
                FormatoExportacion.valueOf(rs.getString("formato")), rs.getBytes("contenido")), id, usuario).stream()
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Archivo inexistente"));
    }

    public void leer(String usuario, UUID id) {
        int filas = jdbc.update("""
                UPDATE comportamiento.ejecucion_reporte SET leida=TRUE WHERE id=? AND programacion IN
                (SELECT id FROM comportamiento.reporte_programado WHERE usuario=?)
                """, id, usuario);
        if (filas == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aviso inexistente");
    }
}
