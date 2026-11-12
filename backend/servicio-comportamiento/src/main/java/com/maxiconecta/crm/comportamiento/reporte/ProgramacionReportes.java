package com.maxiconecta.crm.comportamiento.reporte;

import com.maxiconecta.crm.comportamiento.periodo.RangoPeriodo;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Reportes programados de cada usuario (SCRUM-61, docs/compra/reportes-programados.md). El bloqueo de la fila
 * serializa la ejecución, la edición y la cancelación de una misma programación.
 */
@Service
public class ProgramacionReportes {

    /** Zona en la que se interpretan las fechas programadas y los días completos del reporte. */
    static final ZoneId ZONA = ZoneId.of("America/La_Paz");
    /** Ejecuciones por página del historial. */
    static final int TAMANO_PAGINA = 50;
    /** Programaciones vencidas que toma cada pasada del planificador. */
    static final int LOTE_DE_EJECUCION = 50;
    /** Largo de la columna {@code ejecucion_reporte.causa}. */
    static final int LARGO_CAUSA = 500;
    private static final int PAGINA_MAXIMA = 1_000_000;

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
        // El reporte se genera en su propia transacción: si falla, la ejecución igual queda registrada como FALLIDA.
        generacion = new TransactionTemplate(gestor);
        generacion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record Programacion(UUID id, String nombre, SolicitudProgramacion.Frecuencia frecuencia,
                               FormatoExportacion formato, int dias, String segmento, OffsetDateTime inicio,
                               OffsetDateTime fin, OffsetDateTime proxima, EstadoProgramacion estado) {
    }

    public record Ejecucion(UUID id, UUID programacion, String reporte, OffsetDateTime prevista,
                            OffsetDateTime ejecutada, ResultadoEjecucion estado, String causa, String nombre,
                            boolean leida) {
    }

    private static Programacion fila(ResultSet rs) throws SQLException {
        return new Programacion(rs.getObject("id", UUID.class), rs.getString("nombre"),
                SolicitudProgramacion.Frecuencia.valueOf(rs.getString("frecuencia")),
                FormatoExportacion.valueOf(rs.getString("formato")), rs.getInt("dias"), rs.getString("segmento"),
                rs.getObject("inicio", OffsetDateTime.class), rs.getObject("fin", OffsetDateTime.class),
                rs.getObject("proxima", OffsetDateTime.class), EstadoProgramacion.valueOf(rs.getString("estado")));
    }

    private static Ejecucion ejecucion(ResultSet rs) throws SQLException {
        return new Ejecucion(rs.getObject("id", UUID.class), rs.getObject("programacion", UUID.class),
                rs.getString("reporte"), rs.getObject("prevista", OffsetDateTime.class),
                rs.getObject("ejecutada", OffsetDateTime.class), ResultadoEjecucion.valueOf(rs.getString("estado")),
                rs.getString("causa"), rs.getString("nombre"), rs.getBoolean("leida"));
    }

    public List<Programacion> listar(String usuario) {
        return jdbc.query("SELECT * FROM comportamiento.reporte_programado WHERE usuario=? ORDER BY proxima,id",
                (rs, n) -> fila(rs), usuario);
    }

    /** Crea una programación ({@code id == null}) o reemplaza la configuración de una propia que no esté cancelada. */
    public UUID guardar(String usuario, UUID id, SolicitudProgramacion s) {
        if (!s.inicio().isAfter(OffsetDateTime.now(reloj)) || (s.fin() != null && s.fin().isBefore(s.inicio()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El inicio debe ser futuro y el fin no puede precederlo");
        }
        UUID clave = id == null ? UUID.randomUUID() : id;
        tx.executeWithoutResult(t -> {
            if (id != null) {
                if (obtener(usuario, id).estado() == EstadoProgramacion.CANCELADA) {
                    throw conflicto();
                }
                jdbc.update("""
                        UPDATE comportamiento.reporte_programado SET nombre=?,frecuencia=?,formato=?,dias=?,segmento=?,
                        inicio=?,fin=?,proxima=? WHERE id=? AND usuario=?
                        """, s.nombre().strip(), s.frecuencia().name(), s.formato().name(), s.dias(), s.segmento(),
                        s.inicio(), s.fin(), s.inicio(), id, usuario);
            } else {
                jdbc.update("""
                        INSERT INTO comportamiento.reporte_programado
                        (id,usuario,nombre,frecuencia,formato,dias,segmento,inicio,fin,proxima,estado)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?)
                        """, clave, usuario, s.nombre().strip(), s.frecuencia().name(), s.formato().name(), s.dias(),
                        s.segmento(), s.inicio(), s.fin(), s.inicio(), EstadoProgramacion.ACTIVA.name());
            }
        });
        return clave;
    }

    /** La programación propia, bloqueada hasta el fin de la transacción; 404 si no existe o es de otro usuario. */
    private Programacion obtener(String usuario, UUID id) {
        return jdbc.query("SELECT * FROM comportamiento.reporte_programado WHERE id=? AND usuario=? FOR UPDATE",
                        (rs, n) -> fila(rs), id, usuario).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programación inexistente"));
    }

    /** Pausa, activa o cancela. Al activar, la próxima fecha salta las ocurrencias que quedaron en el pasado. */
    public void estado(String usuario, UUID id, EstadoProgramacion estado) {
        tx.executeWithoutResult(t -> {
            Programacion p = obtener(usuario, id);
            if (p.estado() == EstadoProgramacion.CANCELADA) {
                throw conflicto();
            }
            OffsetDateTime proxima = p.proxima();
            if (estado == EstadoProgramacion.ACTIVA) {
                proxima = primeraDesde(p, proxima, OffsetDateTime.now(reloj));
                if (fueraDeVigencia(p, proxima)) {
                    throw conflicto();
                }
            }
            jdbc.update("UPDATE comportamiento.reporte_programado SET estado=?,proxima=? WHERE id=?",
                    estado.name(), proxima, id);
        });
    }

    private static ResponseStatusException conflicto() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "La programación está cancelada o su vigencia terminó");
    }

    private static boolean fueraDeVigencia(Programacion p, OffsetDateTime fecha) {
        return p.fin() != null && fecha.isAfter(p.fin());
    }

    /** La ocurrencia que sigue a {@code fecha}. La mensual conserva el día del inicio o el último del mes si no existe. */
    static OffsetDateTime siguiente(Programacion p, OffsetDateTime fecha) {
        ZonedDateTime local = fecha.atZoneSameInstant(ZONA);
        return switch (p.frecuencia()) {
            case DIARIA -> local.plusDays(1).toOffsetDateTime();
            case SEMANAL -> local.plusWeeks(1).toOffsetDateTime();
            case MENSUAL -> {
                ZonedDateTime mes = local.plusMonths(1);
                int diaAncla = p.inicio().atZoneSameInstant(ZONA).getDayOfMonth();
                yield mes.withDayOfMonth(Math.min(diaAncla, mes.toLocalDate().lengthOfMonth())).toOffsetDateTime();
            }
        };
    }

    /** La primera ocurrencia de la serie, desde {@code fecha}, que no quedó antes de {@code ahora}. */
    static OffsetDateTime primeraDesde(Programacion p, OffsetDateTime fecha, OffsetDateTime ahora) {
        OffsetDateTime proxima = fecha;
        while (proxima.isBefore(ahora)) {
            proxima = siguiente(p, proxima);
        }
        return proxima;
    }

    /**
     * La primera ocurrencia posterior a {@code fecha} que quede después de {@code ahora}: tras una caída, las que
     * vencieron mientras tanto se saltan y el reporte se genera una sola vez.
     */
    static OffsetDateTime primeraPosterior(Programacion p, OffsetDateTime fecha, OffsetDateTime ahora) {
        OffsetDateTime proxima = siguiente(p, fecha);
        while (!proxima.isAfter(ahora)) {
            proxima = siguiente(p, proxima);
        }
        return proxima;
    }

    /** Un lote acotado; SKIP LOCKED evita duplicados entre instancias. Se procesa una vez tras una caída. */
    public void ejecutarPendientes() {
        List<UUID> ids = jdbc.query("""
                SELECT id FROM comportamiento.reporte_programado
                WHERE estado=? AND proxima<=? ORDER BY proxima LIMIT ?
                """, (rs, n) -> rs.getObject(1, UUID.class), EstadoProgramacion.ACTIVA.name(),
                OffsetDateTime.now(reloj), LOTE_DE_EJECUCION);
        for (UUID id : ids) {
            ejecutar(id);
        }
    }

    void ejecutar(UUID id) {
        tx.executeWithoutResult(t -> {
            OffsetDateTime ahora = OffsetDateTime.now(reloj);
            var filas = jdbc.query("""
                    SELECT * FROM comportamiento.reporte_programado WHERE id=? AND estado=?
                    AND proxima<=? FOR UPDATE SKIP LOCKED
                    """, (rs, n) -> fila(rs), id, EstadoProgramacion.ACTIVA.name(), ahora);
            if (filas.isEmpty()) {
                return;
            }
            Programacion p = filas.get(0);
            if (fueraDeVigencia(p, p.proxima())) {
                jdbc.update("UPDATE comportamiento.reporte_programado SET estado=? WHERE id=?",
                        EstadoProgramacion.PAUSADA.name(), id);
                return;
            }
            String usuario = jdbc.queryForObject("SELECT usuario FROM comportamiento.reporte_programado WHERE id=?",
                    String.class, id);
            ArchivoExportado archivo = null;
            String causa = null;
            try {
                LocalDate hasta = p.proxima().atZoneSameInstant(ZONA).toLocalDate().minusDays(1);
                archivo = generacion.execute(g -> exportacion.comportamiento(
                        new RangoPeriodo(hasta.minusDays(p.dias() - 1L), hasta), p.segmento(), p.formato(), usuario));
            } catch (RuntimeException ex) {
                causa = causa(ex);
            }
            jdbc.update("""
                    INSERT INTO comportamiento.ejecucion_reporte
                    (id,programacion,prevista,ejecutada,estado,causa,nombre,formato,contenido) VALUES (?,?,?,?,?,?,?,?,?)
                    """, UUID.randomUUID(), id, p.proxima(), ahora,
                    (archivo == null ? ResultadoEjecucion.FALLIDA : ResultadoEjecucion.COMPLETADA).name(), causa,
                    archivo == null ? null : archivo.nombre(), p.formato().name(),
                    archivo == null ? null : archivo.contenido());
            OffsetDateTime proxima = primeraPosterior(p, p.proxima(), ahora);
            EstadoProgramacion estado = fueraDeVigencia(p, proxima) ? EstadoProgramacion.PAUSADA : EstadoProgramacion.ACTIVA;
            jdbc.update("UPDATE comportamiento.reporte_programado SET proxima=?,estado=? WHERE id=?",
                    proxima, estado.name(), id);
        });
    }

    /** La causa que ve el usuario en el historial, recortada al largo de la columna. */
    static String causa(RuntimeException ex) {
        String causa = ex.getClass().getSimpleName() + ": " + Objects.toString(ex.getMessage(), "Sin detalle");
        return causa.substring(0, Math.min(LARGO_CAUSA, causa.length()));
    }

    public List<Ejecucion> historial(String usuario, int pagina) {
        if (pagina < 0 || pagina > PAGINA_MAXIMA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página inválida");
        }
        return jdbc.query("""
                SELECT e.*,p.nombre AS reporte FROM comportamiento.ejecucion_reporte e
                JOIN comportamiento.reporte_programado p ON p.id=e.programacion WHERE p.usuario=?
                ORDER BY e.ejecutada DESC,e.id LIMIT ? OFFSET ?
                """, (rs, n) -> ejecucion(rs), usuario, TAMANO_PAGINA, pagina * TAMANO_PAGINA);
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
                        ON p.id=e.programacion WHERE e.id=? AND p.usuario=? AND e.estado=?
                        """, (rs, n) -> new ArchivoExportado(rs.getString("nombre"),
                        FormatoExportacion.valueOf(rs.getString("formato")), rs.getBytes("contenido")),
                        id, usuario, ResultadoEjecucion.COMPLETADA.name()).stream()
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Archivo inexistente"));
    }

    public void leer(String usuario, UUID id) {
        int filas = jdbc.update("""
                UPDATE comportamiento.ejecucion_reporte SET leida=TRUE WHERE id=? AND programacion IN
                (SELECT id FROM comportamiento.reporte_programado WHERE usuario=?)
                """, id, usuario);
        if (filas == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aviso inexistente");
        }
    }
}
