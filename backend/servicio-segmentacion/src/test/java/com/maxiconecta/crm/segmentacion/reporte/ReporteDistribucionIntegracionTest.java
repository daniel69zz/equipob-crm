package com.maxiconecta.crm.segmentacion.reporte;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxiconecta.crm.segmentacion.asignacion.AsignacionSegmento;
import com.maxiconecta.crm.segmentacion.asignacion.MetricasCliente;
import com.maxiconecta.crm.segmentacion.criterio.Criterio.Condicion;
import com.maxiconecta.crm.segmentacion.criterio.Criterio.Indicador;
import com.maxiconecta.crm.segmentacion.criterio.CriterioService;
import com.maxiconecta.crm.segmentacion.criterio.SolicitudCriterio;
import com.maxiconecta.crm.segmentacion.reporte.NivelesFidelizacion.ConteoNiveles;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-57 · Flujo del reporte de distribución de clientes con PostgreSQL real: los conteos por
 * segmento y por nivel de consumo salen de la base (criterio 1), un filtro sin clientes responde
 * "sin datos" en vez de un error (criterio 2), el Agente de Atención no puede abrirlo (criterio 3) y
 * cada generación queda en la auditoría. servicio-fidelizacion se reemplaza por un doble.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReporteDistribucionIntegracionTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String PERMISOS_GERENTE =
            "CLIENTE_CONSULTAR,INDICADORES_CONSULTAR,SEGMENTOS_CONSULTAR,PUNTOS_CONSULTAR,"
                    + "INTERACCIONES_CONSULTAR,REPORTES_CONSULTAR";
    private static final String PERMISOS_AGENTE =
            "CLIENTE_CONSULTAR,INDICADORES_CONSULTAR,PUNTOS_CONSULTAR,INTERACCIONES_CONSULTAR,"
                    + "INTERACCIONES_REGISTRAR";
    private static final OffsetDateTime INICIO = OffsetDateTime.of(2026, 11, 9, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final String RUTA = "/api/segmentacion/reportes/distribucion";

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registro) {
        // Las migraciones reales del Gateway son dueñas de la auditoría compartida.
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
    private NivelesFidelizacion fidelizacion;

    @BeforeEach
    void limpiar() {
        jdbc.execute("TRUNCATE segmentacion.segmento_cliente, segmentacion.metricas_cliente, "
                + "segmentacion.evento_procesado");
        jdbc.update("DELETE FROM segmentacion.criterio");
        when(fidelizacion.todos()).thenReturn(new ConteoNiveles(Map.of(), true));
        when(fidelizacion.deClientes(anyList())).thenReturn(new ConteoNiveles(Map.of(), true));
    }

    // --- Criterio 1: cantidad y porcentaje por segmento, nivel de consumo y nivel de fidelización ---

    @Test
    void cuentaLosClientesPorSegmentoNivelDeConsumoYNivelDeFidelizacion() throws Exception {
        crear("Alto valor", Indicador.VALOR, Condicion.MAYOR_IGUAL, "5000");
        aplicar(501, 12, "6200.00", "ALTO");
        aplicar(502, 30, "9100.50", "ALTO");
        aplicar(503, 1, "80.00", "BAJO");
        aplicar(504, 4, "900.00", "MEDIO");
        when(fidelizacion.todos()).thenReturn(new ConteoNiveles(Map.of("ORO", 2L, "BRONCE", 1L), true));

        consultar(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.sinDatos").value(false))
                .andExpect(jsonPath("$.fidelizacionDisponible").value(true))
                .andExpect(jsonPath("$.filtro.segmento").value(nullValue()))
                .andExpect(jsonPath("$.filtro.nivelConsumo").value(nullValue()))
                // Dos clientes en "Alto valor" y dos sin segmento; el grupo sin segmento va al final.
                .andExpect(jsonPath("$.segmentos[*].valor", contains("Alto valor", null)))
                .andExpect(jsonPath("$.segmentos[0].clientes").value(2))
                .andExpect(jsonPath("$.segmentos[0].porcentaje").value(50.00))
                .andExpect(jsonPath("$.segmentos[1].clientes").value(2))
                // Niveles de consumo de BAJO a ALTO.
                .andExpect(jsonPath("$.nivelesConsumo[*].valor", contains("BAJO", "MEDIO", "ALTO")))
                .andExpect(jsonPath("$.nivelesConsumo[0].clientes").value(1))
                .andExpect(jsonPath("$.nivelesConsumo[0].porcentaje").value(25.00))
                .andExpect(jsonPath("$.nivelesConsumo[2].clientes").value(2))
                .andExpect(jsonPath("$.nivelesConsumo[2].porcentaje").value(50.00))
                // Niveles de fidelización del más bajo al más alto y, al final, el grupo sin nivel.
                .andExpect(jsonPath("$.nivelesFidelizacion[*].valor", contains("BRONCE", "PLATA", "ORO", null)))
                .andExpect(jsonPath("$.nivelesFidelizacion[0].clientes").value(1))
                .andExpect(jsonPath("$.nivelesFidelizacion[2].clientes").value(2))
                .andExpect(jsonPath("$.nivelesFidelizacion[3].clientes").value(1));
    }

    @Test
    void elReporteFiltradoPorSegmentoPideLosNivelesDeEsosClientes() throws Exception {
        crear("Alto valor", Indicador.VALOR, Condicion.MAYOR_IGUAL, "5000");
        aplicar(501, 12, "6200.00", "ALTO");
        aplicar(502, 30, "9100.50", "ALTO");
        aplicar(503, 1, "80.00", "BAJO");
        when(fidelizacion.deClientes(List.of(501L, 502L)))
                .thenReturn(new ConteoNiveles(Map.of("ORO", 2L), true));

        consultar(get(RUTA).param("segmento", "Alto valor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.filtro.segmento").value("Alto valor"))
                .andExpect(jsonPath("$.segmentos", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.segmentos[0].valor").value("Alto valor"))
                .andExpect(jsonPath("$.segmentos[0].porcentaje").value(100.00))
                .andExpect(jsonPath("$.nivelesFidelizacion[2].valor").value("ORO"))
                .andExpect(jsonPath("$.nivelesFidelizacion[2].clientes").value(2))
                .andExpect(jsonPath("$.nivelesFidelizacion[2].porcentaje").value(100.00));
    }

    @Test
    void elFiltroPorNivelDeConsumoReduceLaPoblacion() throws Exception {
        aplicar(501, 12, "6200.00", "ALTO");
        aplicar(503, 1, "80.00", "BAJO");

        consultar(get(RUTA).param("nivelConsumo", "alto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.filtro.nivelConsumo").value("ALTO"))
                .andExpect(jsonPath("$.nivelesConsumo", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.nivelesConsumo[0].valor").value("ALTO"))
                .andExpect(jsonPath("$.nivelesConsumo[0].porcentaje").value(100.00));
    }

    // --- Criterio 2: sin datos para un filtro, se indica sin error ---

    @Test
    void unFiltroSinClientesResponde200ConSinDatos() throws Exception {
        aplicar(501, 12, "6200.00", "ALTO");

        consultar(get(RUTA).param("segmento", "Sin clientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinDatos").value(true))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.segmentos", org.hamcrest.Matchers.hasSize(0)))
                .andExpect(jsonPath("$.nivelesConsumo", org.hamcrest.Matchers.hasSize(0)))
                .andExpect(jsonPath("$.nivelesFidelizacion", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void sinNingunClienteCargadoElReporteTambienDiceSinDatos() throws Exception {
        consultar(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinDatos").value(true))
                .andExpect(jsonPath("$.total").value(0));
    }

    // --- Criterio 3: el Agente de Atención no puede abrir el reporte ---

    @Test
    void elAgenteDeAtencionNoPuedeAbrirElReporte() throws Exception {
        aplicar(501, 12, "6200.00", "ALTO");

        mvc.perform(get(RUTA).header("X-Usuario", "ana").header("X-Usuario-Permisos", PERMISOS_AGENTE))
                .andExpect(status().isForbidden());
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
    }

    // --- Auditoría de la generación ---

    @Test
    void cadaReporteGeneradoQuedaEnLaAuditoria() throws Exception {
        aplicar(501, 12, "6200.00", "ALTO");
        long desde = ultimoEventoAuditoria();

        consultar(get(RUTA).param("nivelConsumo", "ALTO")).andExpect(status().isOk());

        Map<String, Object> evento = jdbc.queryForMap("SELECT usuario, operacion, entidad, entidad_id, detalle "
                + "FROM auditoria.evento WHERE id > ? ORDER BY id DESC LIMIT 1", desde);
        assertThat(evento.get("usuario")).isEqualTo("luis");
        assertThat(evento.get("operacion")).isEqualTo("REPORTE_DISTRIBUCION_GENERADO");
        assertThat(evento.get("entidad")).isEqualTo("REPORTE");
        assertThat(evento.get("entidad_id")).isEqualTo("DISTRIBUCION_CLIENTES");
        JsonNode detalle = json.readTree(evento.get("detalle").toString());
        assertThat(detalle.get("nivelConsumo").asText()).isEqualTo("ALTO");
        assertThat(detalle.get("segmento").isNull()).isTrue();
        assertThat(detalle.get("total").asLong()).isEqualTo(1);
        assertThat(detalle.get("sinDatos").asBoolean()).isFalse();
        assertThat(detalle.get("fidelizacionDisponible").asBoolean()).isTrue();
    }

    @Test
    void unReporteDenegadoNoDejaRegistroDeGeneracion() throws Exception {
        aplicar(501, 12, "6200.00", "ALTO");
        long desde = ultimoEventoAuditoria();

        mvc.perform(get(RUTA).header("X-Usuario", "ana").header("X-Usuario-Permisos", PERMISOS_AGENTE))
                .andExpect(status().isForbidden());

        Long generados = jdbc.queryForObject("SELECT count(*) FROM auditoria.evento "
                + "WHERE operacion = 'REPORTE_DISTRIBUCION_GENERADO' AND id > ?", Long.class, desde);
        assertThat(generados).isZero();
    }

    /**
     * Cada llamada es un evento nuevo: el historial de segmentos es de solo inserción con clave
     * (cliente, evento) y el contenedor se comparte entre pruebas, así que el id no puede repetirse.
     */
    private void aplicar(long cliente, long frecuencia, String valor, String nivel) {
        asignacion.aplicar(new MetricasCliente("ev-" + cliente + "-" + UUID.randomUUID(), "COMPRA_CONFIRMADA", cliente,
                List.of("CLI-" + cliente), frecuencia, new BigDecimal(valor), List.of("Electrónica"), INICIO, null,
                nivel, INICIO));
    }

    private void crear(String nombre, Indicador indicador, Condicion condicion, String minimo) {
        criterios.crear(new SolicitudCriterio(nombre, "", indicador, condicion, new BigDecimal(minimo), null,
                null, null), "admin");
    }

    /** La auditoría es de solo inserción: cada prueba mira los eventos posteriores a este id. */
    private long ultimoEventoAuditoria() {
        Long id = jdbc.queryForObject("SELECT max(id) FROM auditoria.evento", Long.class);
        return id == null ? 0 : id;
    }

    private ResultActions consultar(MockHttpServletRequestBuilder solicitud) throws Exception {
        return mvc.perform(solicitud.header("X-Usuario", "luis").header("X-Usuario-Permisos", PERMISOS_GERENTE));
    }
}
