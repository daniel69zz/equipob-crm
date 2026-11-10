package com.maxiconecta.crm.segmentacion.reporte;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxiconecta.crm.segmentacion.asignacion.AsignacionSegmento;
import com.maxiconecta.crm.segmentacion.asignacion.MetricasCliente;
import com.maxiconecta.crm.segmentacion.criterio.Criterio.Condicion;
import com.maxiconecta.crm.segmentacion.criterio.Criterio.Indicador;
import com.maxiconecta.crm.segmentacion.criterio.CriterioService;
import com.maxiconecta.crm.segmentacion.criterio.SolicitudCriterio;
import com.maxiconecta.crm.segmentacion.reporte.IndicadoresExternos.ClientesActivos;
import com.maxiconecta.crm.segmentacion.reporte.IndicadoresExternos.PuntosAcumulados;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-478 · Panel de indicadores de clientes (SCRUM-59) con PostgreSQL real: la distribución por segmento
 * sale de la base (criterio 1), al recargarlo refleja los cambios (criterio 2), el Agente no puede abrirlo
 * (criterio 3) y cada consulta queda en la auditoría. Los clientes activos y los puntos los dan los otros dos
 * servicios, que aquí se reemplazan por un doble.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PanelIndicadoresIntegracionTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String RUTA = "/api/segmentacion/reportes/panel";
    private static final String PERMISOS_GERENTE = "CLIENTE_CONSULTAR,SEGMENTOS_CONSULTAR,PUNTOS_CONSULTAR,REPORTES_CONSULTAR";
    private static final String PERMISOS_AGENTE = "CLIENTE_CONSULTAR,INDICADORES_CONSULTAR,PUNTOS_CONSULTAR,"
            + "INTERACCIONES_CONSULTAR,INTERACCIONES_REGISTRAR";
    private static final OffsetDateTime INICIO = OffsetDateTime.of(2026, 11, 9, 12, 0, 0, 0, ZoneOffset.UTC);

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registro) {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("seguridad").locations("filesystem:../api-gateway/src/main/resources/db/migration").load()
                .migrate();
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
        registro.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private AsignacionSegmento asignacion;

    @Autowired
    private CriterioService criterios;

    @MockBean
    private IndicadoresExternos externos;

    @BeforeEach
    void limpiar() {
        jdbc.execute("TRUNCATE segmentacion.segmento_cliente, segmentacion.metricas_cliente, "
                + "segmentacion.evento_procesado");
        jdbc.update("DELETE FROM segmentacion.criterio");
        when(externos.clientesActivos()).thenReturn(Optional.of(new ClientesActivos(2, 90)));
        when(externos.puntosAcumulados()).thenReturn(Optional.of(new PuntosAcumulados(110, 2)));
    }

    // --- Criterio 1: clientes activos, distribución por segmento y puntos acumulados ---

    @Test
    void elPanelMuestraClientesActivosDistribucionPorSegmentoYPuntos() throws Exception {
        criterios.crear(new SolicitudCriterio("Alto valor", "", Indicador.VALOR, Condicion.MAYOR_IGUAL,
                new BigDecimal("500"), null, null, null), "admin");
        aplicar(501, "600.00");
        aplicar(502, "500.00");
        aplicar(503, "80.00");

        panel().andExpect(status().isOk())
                .andExpect(jsonPath("$.clientes").value(3))
                .andExpect(jsonPath("$.sinDatos").value(false))
                .andExpect(jsonPath("$.activos.disponible").value(true))
                .andExpect(jsonPath("$.activos.activos").value(2))
                .andExpect(jsonPath("$.activos.porcentaje").value(66.67))
                .andExpect(jsonPath("$.activos.umbralDias").value(90))
                .andExpect(jsonPath("$.segmentos[*].valor", contains("Alto valor", null)))
                .andExpect(jsonPath("$.segmentos[0].clientes").value(2))
                .andExpect(jsonPath("$.segmentos[0].porcentaje").value(66.67))
                .andExpect(jsonPath("$.puntos.puntos").value(110))
                .andExpect(jsonPath("$.puntos.clientesConPuntos").value(2));
    }

    @Test
    void laDistribucionDelPanelCoincideConLaDelReporteDeDistribucion() throws Exception {
        criterios.crear(new SolicitudCriterio("Alto valor", "", Indicador.VALOR, Condicion.MAYOR_IGUAL,
                new BigDecimal("500"), null, null, null), "admin");
        aplicar(501, "600.00");
        aplicar(503, "80.00");

        JsonNode panel = leer(panel());
        JsonNode reporte = leer(mvc.perform(get("/api/segmentacion/reportes/distribucion")
                .header("X-Usuario", "luis").header("X-Usuario-Permisos", PERMISOS_GERENTE)));

        assertThat(panel.get("segmentos")).isEqualTo(reporte.get("segmentos"));
        assertThat(panel.get("clientes").asLong()).isEqualTo(reporte.get("total").asLong());
    }

    // --- Criterio 2: al recargar, los indicadores se actualizan ---

    @Test
    void alRecargarElPanelReflejaLosCambios() throws Exception {
        criterios.crear(new SolicitudCriterio("Alto valor", "", Indicador.VALOR, Condicion.MAYOR_IGUAL,
                new BigDecimal("500"), null, null, null), "admin");
        aplicar(501, "600.00");
        panel().andExpect(jsonPath("$.clientes").value(1))
                .andExpect(jsonPath("$.segmentos[0].clientes").value(1))
                .andExpect(jsonPath("$.puntos.puntos").value(110));

        aplicar(502, "700.00");
        when(externos.puntosAcumulados()).thenReturn(Optional.of(new PuntosAcumulados(180, 2)));

        panel().andExpect(jsonPath("$.clientes").value(2))
                .andExpect(jsonPath("$.segmentos[0].clientes").value(2))
                .andExpect(jsonPath("$.puntos.puntos").value(180));
    }

    @Test
    void sinClientesElPanelRespondeSinDatosYNoEsUnError() throws Exception {
        panel().andExpect(status().isOk())
                .andExpect(jsonPath("$.sinDatos").value(true))
                .andExpect(jsonPath("$.clientes").value(0));
    }

    // --- Criterio 3: un rol sin permiso no abre el panel ---

    @Test
    void elAgenteNoPuedeAbrirElPanelYNoDejaRegistro() throws Exception {
        long desde = ultimoEvento();
        mvc.perform(get(RUTA).header("X-Usuario", "ana").header("X-Usuario-Permisos", PERMISOS_AGENTE))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria.evento WHERE id > ?", Long.class, desde))
                .isZero();
    }

    @Test
    void cadaConsultaDelPanelQuedaEnLaAuditoria() throws Exception {
        aplicar(501, "600.00");
        long desde = ultimoEvento();

        panel().andExpect(status().isOk());

        Map<String, Object> evento = jdbc.queryForMap("SELECT usuario, operacion, entidad_id, detalle "
                + "FROM auditoria.evento WHERE id > ? ORDER BY id DESC LIMIT 1", desde);
        assertThat(evento.get("usuario")).isEqualTo("luis");
        assertThat(evento.get("operacion")).isEqualTo("PANEL_INDICADORES_CONSULTADO");
        assertThat(evento.get("entidad_id")).isEqualTo("PANEL_INDICADORES");
        JsonNode detalle = json.readTree(evento.get("detalle").toString());
        assertThat(detalle.get("clientes").asLong()).isEqualTo(1);
        assertThat(detalle.get("activosDisponible").asBoolean()).isTrue();
    }

    private ResultActions panel() throws Exception {
        return mvc.perform(get(RUTA).header("X-Usuario", "luis").header("X-Usuario-Permisos", PERMISOS_GERENTE));
    }

    private JsonNode leer(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private long ultimoEvento() {
        Long id = jdbc.queryForObject("SELECT max(id) FROM auditoria.evento", Long.class);
        return id == null ? 0 : id;
    }

    /** Cada llamada es un evento nuevo: el historial de segmentos es de solo inserción. */
    private void aplicar(long cliente, String valor) {
        asignacion.aplicar(new MetricasCliente("ev-" + cliente + "-" + UUID.randomUUID(), "COMPRA_CONFIRMADA",
                cliente, List.of("CLI-" + cliente), 1L, new BigDecimal(valor), List.of("Hogar"), INICIO, null,
                "ALTO", INICIO));
    }
}
