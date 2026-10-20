package com.maxiconecta.crm.segmentacion.consulta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxiconecta.crm.segmentacion.asignacion.AsignacionSegmento;
import com.maxiconecta.crm.segmentacion.asignacion.MetricasCliente;
import com.maxiconecta.crm.segmentacion.consulta.PerfilesClientes.Nombre;
import com.maxiconecta.crm.segmentacion.consulta.PerfilesClientes.NombresClientes;
import com.maxiconecta.crm.segmentacion.criterio.Criterio;
import com.maxiconecta.crm.segmentacion.criterio.Criterio.Condicion;
import com.maxiconecta.crm.segmentacion.criterio.Criterio.Indicador;
import com.maxiconecta.crm.segmentacion.criterio.CriterioService;
import com.maxiconecta.crm.segmentacion.criterio.SolicitudCriterio;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-279 · Flujo de la consulta de clientes por segmento o nivel de consumo (SCRUM-28) con
 * PostgreSQL real: listado paginado, filtros y orden, segmento vacío, segmento grande, permisos y
 * auditoría. servicio-perfil se reemplaza por un doble que devuelve los nombres.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ClientesSegmentoIntegracionTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String PERMISOS_GERENTE =
            "CLIENTE_CONSULTAR,INDICADORES_CONSULTAR,SEGMENTOS_CONSULTAR,PUNTOS_CONSULTAR,INTERACCIONES_CONSULTAR";
    private static final String PERMISOS_AGENTE =
            "CLIENTE_CONSULTAR,INDICADORES_CONSULTAR,PUNTOS_CONSULTAR,INTERACCIONES_CONSULTAR,INTERACCIONES_REGISTRAR";
    private static final OffsetDateTime INICIO = OffsetDateTime.of(2026, 10, 13, 12, 0, 0, 0, ZoneOffset.UTC);

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registro) {
        // Las migraciones reales del Gateway son dueñas de la auditoría compartida.
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("seguridad").locations("filesystem:../api-gateway/src/main/resources/db/migration").load()
                .migrate();
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
        // La asignación se llama directamente; el consumo por RabbitMQ se prueba en AsignacionSegmentoIntegracionTest.
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
    private PerfilesClientes perfiles;

    @BeforeEach
    void limpiar() {
        jdbc.execute("TRUNCATE segmentacion.segmento_cliente, segmentacion.metricas_cliente, segmentacion.evento_procesado");
        jdbc.update("DELETE FROM segmentacion.criterio");
        when(perfiles.buscar(anyCollection())).thenAnswer(invocacion -> nombres(invocacion.getArgument(0)));
    }

    // --- Criterio 1: listado paginado con nombre, segmento, nivel de consumo y última compra ---

    @Test
    void listaLosClientesDelSegmentoConNombreNivelYUltimaCompra() throws Exception {
        crear("Alto valor", Indicador.VALOR, Condicion.MAYOR_IGUAL, "5000", null, null);
        // Las métricas llegan como las publica servicio-comportamiento (SCRUM-277) y asignan el segmento.
        aplicar(501, 12, "6200.00", "ALTO", "2026-10-10T15:00:00Z");
        aplicar(502, 30, "9100.50", "ALTO", "2026-09-02T15:00:00Z");
        aplicar(503, 1, "80.00", "BAJO", "2026-10-01T15:00:00Z");

        consultar(get("/api/segmentacion/clientes").param("segmento", "Alto valor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanio").value(20))
                .andExpect(jsonPath("$.orden").value("valor"))
                .andExpect(jsonPath("$.direccion").value("desc"))
                .andExpect(jsonPath("$.nombresDisponibles").value(true))
                .andExpect(jsonPath("$.content[*].clienteId", contains(502, 501)))
                .andExpect(jsonPath("$.content[1].nombres").value("Ana"))
                .andExpect(jsonPath("$.content[1].apellidos").value("Pérez"))
                .andExpect(jsonPath("$.content[1].segmento").value("Alto valor"))
                .andExpect(jsonPath("$.content[1].indicador").value("VALOR"))
                .andExpect(jsonPath("$.content[1].nivelConsumo").value("ALTO"))
                .andExpect(jsonPath("$.content[1].valorAcumulado").value(6200.00))
                .andExpect(jsonPath("$.content[1].frecuencia").value(12))
                .andExpect(jsonPath("$.content[1].ultimaCompra").value("2026-10-10T15:00:00Z"));
    }

    @Test
    void unMensajeSinNivelNiUltimaCompraSeAplicaIgualYSeListaSinEsosDatos() throws Exception {
        crear("Alto valor", Indicador.VALOR, Condicion.MAYOR_IGUAL, "5000", null, null);
        aplicar(504, 12, "6200.00", null, null);

        consultar(get("/api/segmentacion/clientes").param("segmento", "Alto valor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].clienteId").value(504))
                .andExpect(jsonPath("$.content[0].nivelConsumo").value(nullValue()))
                .andExpect(jsonPath("$.content[0].ultimaCompra").value(nullValue()));
    }

    // --- Criterio 2: filtros y orden sin perder el segmento seleccionado ---

    @Test
    void filtrarYOrdenarConservaElSegmento() throws Exception {
        habitualesYLectores();

        consultar(get("/api/segmentacion/clientes").param("segmento", "Habituales"))
                .andExpect(jsonPath("$.content[*].clienteId", contains(604, 602, 601, 603)));
        consultar(get("/api/segmentacion/clientes").param("segmento", "Habituales").param("nivelConsumo", "alto"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.content[*].clienteId", contains(604, 602)));
        consultar(get("/api/segmentacion/clientes").param("segmento", "Habituales")
                .param("orden", "ultimaCompra").param("direccion", "asc"))
                .andExpect(jsonPath("$.content[*].clienteId", contains(602, 601, 603, 604)));
        // Sin fecha va al final también en orden descendente.
        consultar(get("/api/segmentacion/clientes").param("segmento", "Habituales")
                .param("orden", "ultimaCompra").param("direccion", "desc"))
                .andExpect(jsonPath("$.content[*].clienteId", contains(603, 601, 602, 604)));
        consultar(get("/api/segmentacion/clientes").param("segmento", "Habituales").param("orden", "nivelConsumo"))
                .andExpect(jsonPath("$.content[*].clienteId", contains(602, 604, 601, 603)));
        consultar(get("/api/segmentacion/clientes").param("segmento", "Habituales")
                .param("ultimaCompraHasta", "2026-09-10"))
                .andExpect(jsonPath("$.content[*].clienteId", contains(602, 601)));
        consultar(get("/api/segmentacion/clientes").param("segmento", "Habituales")
                .param("ultimaCompraDesde", "2026-09-01").param("ultimaCompraHasta", "2026-09-30"))
                .andExpect(jsonPath("$.content[*].clienteId", contains(601)));
    }

    @Test
    void porNivelDeConsumoSeListanClientesDeVariosSegmentosYSinSegmento() throws Exception {
        habitualesYLectores();

        consultar(get("/api/segmentacion/clientes").param("nivelConsumo", "ALTO"))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.content[*].clienteId", contains(605, 604, 602, 606)))
                .andExpect(jsonPath("$.content[0].segmento").value("Lectores"))
                .andExpect(jsonPath("$.content[3].segmento").value(nullValue()));
    }

    // --- Criterio 3: un segmento sin clientes responde una lista vacía ---

    @Test
    void unSegmentoSinClientesRespondeUnaListaVaciaYApareceEnElCatalogo() throws Exception {
        habitualesYLectores();

        consultar(get("/api/segmentacion/clientes").param("segmento", "Valor simbólico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.content").isEmpty());

        consultar(get("/api/segmentacion/segmentos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].segmento", contains("FRECUENTE", "Habituales", "Lectores", "Valor simbólico")))
                .andExpect(jsonPath("$[0].criterioActivo").value(nullValue()))
                .andExpect(jsonPath("$[0].clientes").value(1))
                .andExpect(jsonPath("$[1].criterioActivo").value(true))
                .andExpect(jsonPath("$[1].indicador").value("FRECUENCIA"))
                .andExpect(jsonPath("$[1].clientes").value(4))
                .andExpect(jsonPath("$[3].clientes").value(0));
    }

    // --- Criterio 4: permiso y auditoría ---

    @Test
    void cadaConsultaAutorizadaQuedaAuditadaConElGrupoYLosClientesMostrados() throws Exception {
        habitualesYLectores();
        long antes = ultimoEventoAuditoria();

        consultar(get("/api/segmentacion/clientes").param("segmento", "Habituales").param("nivelConsumo", "ALTO")
                .param("ultimaCompraDesde", "2026-08-01"))
                .andExpect(status().isOk());

        Map<String, Object> evento = jdbc.queryForMap("SELECT usuario, operacion, entidad, entidad_id, detalle "
                + "FROM auditoria.evento WHERE id > ? AND operacion = 'CLIENTES_SEGMENTO_CONSULTADOS'", antes);
        assertThat(evento).containsEntry("usuario", "luis").containsEntry("entidad", "SEGMENTO")
                .containsEntry("entidad_id", "Habituales");
        JsonNode detalle = json.readTree((String) evento.get("detalle"));
        assertThat(detalle.get("segmento").asText()).isEqualTo("Habituales");
        assertThat(detalle.get("nivelConsumo").asText()).isEqualTo("ALTO");
        assertThat(detalle.get("ultimaCompraDesde").asText()).isEqualTo("2026-08-01");
        assertThat(detalle.get("total").asLong()).isEqualTo(1);
        assertThat(detalle.get("clientes").toString()).isEqualTo("[602]");
        // Sin datos personales en el registro.
        assertThat(detalle.toString()).doesNotContain("Cliente", "Pérez");
    }

    @Test
    void unaConsultaSoloPorNivelSeAuditaConElNivelComoEntidad() throws Exception {
        habitualesYLectores();
        long antes = ultimoEventoAuditoria();

        consultar(get("/api/segmentacion/clientes").param("nivelConsumo", "BAJO")).andExpect(status().isOk());

        assertThat(jdbc.queryForMap("SELECT entidad, entidad_id FROM auditoria.evento WHERE id > ?", antes))
                .containsEntry("entidad", "NIVEL_CONSUMO").containsEntry("entidad_id", "BAJO");
    }

    @Test
    void unRolSinPermisoOSinIdentidadSeRechazaYNoSeMuestraNiSeAuditaElGrupo() throws Exception {
        habitualesYLectores();
        long antes = ultimoEventoAuditoria();

        mvc.perform(get("/api/segmentacion/clientes").param("segmento", "Habituales")
                        .header("X-Usuario", "ana").header("X-Usuario-Permisos", PERMISOS_AGENTE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.content").doesNotExist());
        mvc.perform(get("/api/segmentacion/clientes").param("segmento", "Habituales"))
                .andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria.evento WHERE id > ?", Long.class, antes))
                .isZero();
    }

    // --- Definición de terminado: segmentos grandes y vacíos ---

    @Test
    void unSegmentoGrandeSePaginaSinRepetirNiPerderClientes() throws Exception {
        List<Object[]> segmentos = new ArrayList<>();
        List<Object[]> metricas = new ArrayList<>();
        for (long cliente = 10_001; cliente <= 10_250; cliente++) {
            segmentos.add(new Object[]{cliente, "Masivo"});
            // Valores repetidos a propósito: el desempate por cliente mantiene la paginación estable.
            metricas.add(new Object[]{cliente, new BigDecimal(cliente % 7 * 100)});
        }
        jdbc.batchUpdate("INSERT INTO segmentacion.segmento_cliente (cliente_id, segmento, asignado_en) "
                + "VALUES (?, ?, now())", segmentos);
        jdbc.batchUpdate("INSERT INTO segmentacion.metricas_cliente (cliente_id, frecuencia, valor, calculadas_en, "
                + "id_evento_origen) VALUES (?, 1, ?, now(), 'carga')", metricas);

        Set<Long> vistos = new HashSet<>();
        int[] esperados = {100, 100, 50};
        for (int pagina = 0; pagina < esperados.length; pagina++) {
            JsonNode respuesta = leer(consultar(get("/api/segmentacion/clientes").param("segmento", "Masivo")
                    .param("pagina", String.valueOf(pagina)).param("tamanio", "100")));
            assertThat(respuesta.get("total").asLong()).isEqualTo(250);
            assertThat(respuesta.get("content")).hasSize(esperados[pagina]);
            respuesta.get("content").forEach(cliente -> vistos.add(cliente.get("clienteId").asLong()));
        }
        assertThat(vistos).hasSize(250);

        // Una página mayor que el máximo se recorta a 100.
        consultar(get("/api/segmentacion/clientes").param("segmento", "Masivo").param("tamanio", "1000"))
                .andExpect(jsonPath("$.tamanio").value(100))
                .andExpect(jsonPath("$.content.length()").value(100));
    }

    // --- servicio-perfil no disponible ---

    @Test
    void sinServicioDePerfilElListadoSeMuestraSinNombres() throws Exception {
        habitualesYLectores();
        when(perfiles.buscar(anyCollection())).thenReturn(new NombresClientes(Map.of(), false));

        consultar(get("/api/segmentacion/clientes").param("segmento", "Habituales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombresDisponibles").value(false))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.content[0].nombres").value(nullValue()));
    }

    // --- Utilidades ---

    /**
     * «Habituales» (FRECUENCIA): 601 MEDIO, 602 ALTO, 603 BAJO y 604 ALTO sin fecha de última compra.
     * «Lectores» (CATEGORIA): 605 ALTO. 606 ALTO sin segmento. «FRECUENTE»: un segmento anterior a
     * SCRUM-26 sin criterio. «Valor simbólico»: criterio activo sin clientes. «Antiguo»: inactivo.
     */
    private void habitualesYLectores() {
        crear("Habituales", Indicador.FRECUENCIA, Condicion.ENTRE, "2", "9", null);
        crear("Lectores", Indicador.CATEGORIA, Condicion.CONTIENE, null, null, "Libros");
        crear("Valor simbólico", Indicador.VALOR, Condicion.ENTRE, "0", "10", null);
        Criterio antiguo = crear("Antiguo", Indicador.VALOR, Condicion.ENTRE, "20", "30", null);
        criterios.cambiarEstado(antiguo.id(), false, antiguo.version(), "admin");

        cliente(601, "Habituales", "FRECUENCIA", "MEDIO", "800.00", 4, "2026-09-05T15:00:00Z");
        cliente(602, "Habituales", "FRECUENCIA", "ALTO", "5200.00", 6, "2026-08-20T15:00:00Z");
        cliente(603, "Habituales", "FRECUENCIA", "BAJO", "120.00", 2, "2026-10-01T15:00:00Z");
        cliente(604, "Habituales", "FRECUENCIA", "ALTO", "7000.00", 9, null);
        cliente(605, "Lectores", "CATEGORIA", "ALTO", "9000.00", 1, "2026-10-05T15:00:00Z");
        cliente(606, null, null, "ALTO", "5100.00", 1, "2026-10-06T15:00:00Z");
        cliente(607, "FRECUENTE", null, "BAJO", "50.00", 1, "2026-10-07T15:00:00Z");
    }

    private void cliente(long id, String segmento, String indicador, String nivel, String valor, long frecuencia,
                         String ultimaCompra) {
        if (segmento != null) {
            jdbc.update("INSERT INTO segmentacion.segmento_cliente (cliente_id, segmento, indicador, asignado_en) "
                    + "VALUES (?, ?, ?, ?)", id, segmento, indicador, INICIO);
        }
        jdbc.update("""
                INSERT INTO segmentacion.metricas_cliente
                    (cliente_id, frecuencia, valor, nivel_consumo, ultima_compra, calculadas_en, id_evento_origen)
                VALUES (?, ?, ?, ?, ?::timestamptz, ?, ?)
                """, id, frecuencia, new BigDecimal(valor), nivel, ultimaCompra, INICIO, "ev-" + id);
    }

    private void aplicar(long cliente, long frecuencia, String valor, String nivel, String ultimaCompra) {
        asignacion.aplicar(new MetricasCliente("ev-" + cliente, "COMPRA_CONFIRMADA", cliente,
                List.of("CLI-" + cliente), frecuencia, new BigDecimal(valor), List.of("Electrónica"), INICIO, null,
                nivel, ultimaCompra == null ? null : OffsetDateTime.parse(ultimaCompra)));
    }

    private Criterio crear(String nombre, Indicador indicador, Condicion condicion, String minimo, String maximo,
                           String categoria) {
        return criterios.crear(new SolicitudCriterio(nombre, "", indicador, condicion,
                minimo == null ? null : new BigDecimal(minimo), maximo == null ? null : new BigDecimal(maximo),
                categoria, null), "admin");
    }

    private ResultActions consultar(MockHttpServletRequestBuilder solicitud) throws Exception {
        return mvc.perform(solicitud.header("X-Usuario", "luis").header("X-Usuario-Permisos", PERMISOS_GERENTE));
    }

    private JsonNode leer(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private long ultimoEventoAuditoria() {
        Long id = jdbc.queryForObject("SELECT max(id) FROM auditoria.evento", Long.class);
        return id == null ? 0 : id;
    }

    private static NombresClientes nombres(Collection<Long> clientes) {
        Map<Long, Nombre> nombres = new LinkedHashMap<>();
        for (Long cliente : clientes) {
            nombres.put(cliente, cliente == 501 ? new Nombre("Ana", "Pérez") : new Nombre("Cliente", "N" + cliente));
        }
        return new NombresClientes(nombres, true);
    }
}
