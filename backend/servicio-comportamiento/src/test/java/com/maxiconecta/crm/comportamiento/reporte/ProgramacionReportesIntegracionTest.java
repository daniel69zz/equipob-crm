package com.maxiconecta.crm.comportamiento.reporte;

import com.maxiconecta.crm.comportamiento.periodo.RangoPeriodo;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.rabbitmq.listener.simple.auto-startup=false",
        "comportamiento.reportes.programacion.habilitada=false"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ProgramacionReportesIntegracionTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource
    static void auditoria(DynamicPropertyRegistry r) {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas("seguridad").locations("filesystem:../api-gateway/src/main/resources/db/migration").load().migrate();
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired MockMvc mvc;
    @SpyBean ExportacionReportes exportacion;
    private ProgramacionReportes servicio;
    private static final String URL = "/api/comportamiento/reportes/programaciones";
    private static final String PERMISOS = "REPORTES_CONSULTAR,REPORTES_EXPORTAR,REPORTES_PROGRAMAR";
    private static final OffsetDateTime FECHA = OffsetDateTime.parse("2026-11-10T08:00:00-04:00");

    @BeforeEach void preparar() {
        jdbc.execute("TRUNCATE comportamiento.ejecucion_reporte, comportamiento.reporte_programado");
        servicio = conReloj(FECHA.minusDays(1));
    }
    ProgramacionReportes conReloj(OffsetDateTime ahora) {
        return new ProgramacionReportes(jdbc, exportacion, Clock.fixed(ahora.toInstant(), ZoneOffset.UTC), gestor);
    }
    SolicitudProgramacion solicitud(FormatoExportacion formato) {
        return new SolicitudProgramacion("Semanal", SolicitudProgramacion.Frecuencia.SEMANAL, formato, 7, null, FECHA, null);
    }
    @Test void semanalGeneraArchivoRealNotificaYNoDuplica() {
        UUID id = servicio.guardar("ana", null, solicitud(FormatoExportacion.CSV));
        servicio = conReloj(FECHA);
        servicio.ejecutarPendientes(); servicio.ejecutarPendientes();
        var historial = servicio.historial("ana", 0);
        assertThat(historial).hasSize(1);
        var e = historial.get(0);
        assertThat(e.estado()).isEqualTo(ResultadoEjecucion.COMPLETADA); assertThat(e.leida()).isFalse();
        var archivo = servicio.descargar("ana", e.id());
        assertThat(new String(archivo.contenido(), java.nio.charset.StandardCharsets.UTF_8)).contains("2026-11-03", "2026-11-09");
        verify(exportacion).comportamiento(new RangoPeriodo(FECHA.toLocalDate().minusDays(7), FECHA.toLocalDate().minusDays(1)),
                null, FormatoExportacion.CSV, "ana");
        assertThat(servicio.listar("ana").get(0).proxima().toInstant()).isEqualTo(FECHA.plusWeeks(1).toInstant());
        servicio.leer("ana", e.id());
        assertThat(servicio.historial("ana", 0).get(0).leida()).isTrue();
        assertThat(id).isEqualTo(e.programacion());
    }
    @Test void generaPdfReal() {
        servicio.guardar("ana", null, solicitud(FormatoExportacion.PDF));
        servicio = conReloj(FECHA); servicio.ejecutarPendientes();
        var e = servicio.historial("ana", 0).get(0);
        assertThat(e.estado()).isEqualTo(ResultadoEjecucion.COMPLETADA);
        assertThat(new String(servicio.descargar("ana", e.id()).contenido(), java.nio.charset.StandardCharsets.ISO_8859_1)).startsWith("%PDF-");
    }
    @Test void canceladaNoGeneraNiSeReactiva() {
        UUID id = servicio.guardar("ana", null, solicitud(FormatoExportacion.CSV));
        servicio.estado("ana", id, EstadoProgramacion.CANCELADA);
        servicio = conReloj(FECHA); servicio.ejecutarPendientes();
        assertThat(servicio.historial("ana", 0)).isEmpty();
        verifyNoInteractions(exportacion);
        assertThatThrownBy(() -> servicio.estado("ana", id, EstadoProgramacion.ACTIVA)).hasMessageContaining("409");
    }
    @Test void falloPersisteCausaAvisaYContinuaConOtrosReportes() {
        doThrow(new IllegalStateException("Segmentación no disponible")).when(exportacion)
                .comportamiento(any(), eq("Alto valor"), any(), eq("ana"));
        servicio.guardar("ana", null, new SolicitudProgramacion("Segmento", SolicitudProgramacion.Frecuencia.SEMANAL,
                FormatoExportacion.CSV, 7, "Alto valor", FECHA, null));
        servicio.guardar("ana", null, solicitud(FormatoExportacion.CSV));
        servicio = conReloj(FECHA); servicio.ejecutarPendientes();
        assertThat(servicio.historial("ana", 0)).hasSize(2).anySatisfy(e -> {
            assertThat(e.estado()).isEqualTo(ResultadoEjecucion.FALLIDA);
            assertThat(e.causa()).contains("Segmentación no disponible"); assertThat(e.leida()).isFalse();
            assertThatThrownBy(() -> servicio.descargar("ana", e.id())).hasMessageContaining("404");
        }).anySatisfy(e -> assertThat(e.estado()).isEqualTo(ResultadoEjecucion.COMPLETADA));
    }
    @Test void pausaEdicionActivacionYFinDeVigencia() {
        UUID id = servicio.guardar("ana", null, solicitud(FormatoExportacion.CSV));
        servicio.estado("ana", id, EstadoProgramacion.PAUSADA);
        servicio = conReloj(FECHA); servicio.ejecutarPendientes();
        assertThat(servicio.historial("ana", 0)).isEmpty();
        var nueva = new SolicitudProgramacion("Diario PDF", SolicitudProgramacion.Frecuencia.DIARIA,
                FormatoExportacion.PDF, 1, null, FECHA.plusDays(1), FECHA.plusDays(1));
        servicio.guardar("ana", id, nueva); servicio.estado("ana", id, EstadoProgramacion.ACTIVA);
        servicio = conReloj(FECHA.plusDays(1)); servicio.ejecutarPendientes();
        assertThat(servicio.historial("ana", 0)).hasSize(1);
        assertThat(servicio.listar("ana").get(0).estado()).isEqualTo(EstadoProgramacion.PAUSADA);
        servicio = conReloj(FECHA.plusDays(2)); servicio.ejecutarPendientes();
        assertThat(servicio.historial("ana", 0)).hasSize(1);
    }
    @Test void dosInstanciasNoDuplican() throws Exception {
        servicio.guardar("ana", null, solicitud(FormatoExportacion.CSV));
        servicio = conReloj(FECHA);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> servicio.ejecutarPendientes());
            var b = pool.submit(() -> servicio.ejecutarPendientes());
            a.get(20, TimeUnit.SECONDS); b.get(20, TimeUnit.SECONDS);
            assertThat(servicio.historial("ana", 0)).hasSize(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void trasCaidaGeneraUnaVezYAvanzaAlFuturo() {
        servicio.guardar("ana", null, solicitud(FormatoExportacion.CSV));
        servicio = conReloj(FECHA.plusWeeks(5)); servicio.ejecutarPendientes();
        assertThat(servicio.historial("ana", 0)).hasSize(1);
        assertThat(servicio.listar("ana").get(0).proxima().toInstant()).isAfter(FECHA.plusWeeks(5).toInstant());
    }
    @Test void otroUsuarioNoConsultaNiModificaNiDescarga() {
        UUID id = servicio.guardar("ana", null, solicitud(FormatoExportacion.CSV));
        assertThat(servicio.listar("luis")).isEmpty();
        assertThatThrownBy(() -> servicio.estado("luis", id, EstadoProgramacion.CANCELADA)).hasMessageContaining("404");
        assertThatThrownBy(() -> servicio.guardar("luis", id, solicitud(FormatoExportacion.CSV))).hasMessageContaining("404");
        servicio = conReloj(FECHA); servicio.ejecutarPendientes();
        UUID ejecucion = servicio.historial("ana", 0).get(0).id();
        assertThat(servicio.historial("luis", 0)).isEmpty();
        assertThatThrownBy(() -> servicio.descargar("luis", ejecucion)).hasMessageContaining("404");
        assertThatThrownBy(() -> servicio.leer("luis", ejecucion)).hasMessageContaining("404");
    }
    @Test void permisosSeValidanEnServicioParaLecturaYEscritura() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        for (String permisos : List.of("REPORTES_CONSULTAR", "REPORTES_CONSULTAR,REPORTES_EXPORTAR", "REPORTES_PROGRAMAR")) {
            mvc.perform(get(URL).header("X-Usuario", "ana").header("X-Usuario-Permisos", permisos)).andExpect(status().isForbidden());
            mvc.perform(post(URL).header("X-Usuario", "ana").header("X-Usuario-Permisos", permisos)
                    .contentType("application/json").content("{}")).andExpect(status().isForbidden());
        }
        mvc.perform(get(URL).header("X-Usuario", "ana").header("X-Usuario-Permisos", PERMISOS)).andExpect(status().isOk());
        mvc.perform(post(URL).header("X-Usuario", "ana").header("X-Usuario-Permisos", PERMISOS)
                .contentType("application/json").content("{}")).andExpect(status().isBadRequest());
    }
    @Test void mensualMantieneDiaAnclaYDiariaCruzaAnio() {
        var inicio = OffsetDateTime.parse("2026-01-31T08:00:00-04:00");
        var p = new ProgramacionReportes.Programacion(UUID.randomUUID(), "Mes", SolicitudProgramacion.Frecuencia.MENSUAL,
                FormatoExportacion.CSV, 30, null, inicio, null, inicio, EstadoProgramacion.ACTIVA);
        var febrero = ProgramacionReportes.siguiente(p, inicio);
        assertThat(febrero.getDayOfMonth()).isEqualTo(28);
        assertThat(ProgramacionReportes.siguiente(p, febrero).getDayOfMonth()).isEqualTo(31);
        assertThatThrownBy(() -> servicio.guardar("ana", null, new SolicitudProgramacion("Pasado",
                SolicitudProgramacion.Frecuencia.DIARIA, FormatoExportacion.CSV, 1, null, FECHA.minusDays(2), null)))
                .hasMessageContaining("400");
    }
    @Test void unErrorInesperadoNoDetieneLasDemasProgramaciones() {
        UUID primera = servicio.guardar("ana", null, solicitud(FormatoExportacion.CSV));
        servicio.guardar("ana", null, new SolicitudProgramacion("Otra", SolicitudProgramacion.Frecuencia.SEMANAL,
                FormatoExportacion.CSV, 7, null, FECHA.plusMinutes(1), null));
        var espia = spy(conReloj(FECHA.plusMinutes(1)));
        doThrow(new IllegalStateException("Conexión perdida")).when(espia).ejecutar(primera);
        espia.ejecutarPendientes();
        assertThat(servicio.historial("ana", 0)).singleElement()
                .satisfies(e -> assertThat(e.reporte()).isEqualTo("Otra"));
        conReloj(FECHA.plusMinutes(1)).ejecutarPendientes();
        assertThat(servicio.historial("ana", 0)).hasSize(2);
    }
    @Test void segmentoEnBlancoEsTodaLaCartera() {
        servicio.guardar("ana", null, new SolicitudProgramacion("Cartera", SolicitudProgramacion.Frecuencia.DIARIA,
                FormatoExportacion.CSV, 1, "   ", FECHA, null));
        assertThat(servicio.listar("ana").get(0).segmento()).isNull();
    }
}
