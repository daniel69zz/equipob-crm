package com.maxiconecta.crm.comportamiento.reporte;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.maxiconecta.crm.comportamiento.comparacion.ClientesDelSegmento;
import com.maxiconecta.crm.comportamiento.compra.ClientePerfiles;
import com.maxiconecta.crm.comportamiento.ingesta.EstadoEvento;
import com.maxiconecta.crm.comportamiento.ingesta.EventoRecibidoRepository;
import com.maxiconecta.crm.comportamiento.ingesta.IngestaCompras;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-485 · Exportación de los reportes de clientes a CSV y PDF (SCRUM-60) con PostgreSQL real y compras por la
 * ingesta: el archivo tiene los mismos datos que la pantalla (criterio 1), un rol sin permiso de exportación recibe
 * 403 (criterio 2) y cada exportación queda en la auditoría con quién, qué y cuándo (criterio 3).
 *
 * <p>Septiembre de 2026: CLI-1 compra 400 (Hogar) y 200 (Tecnología, Hogar), CLI-2 500 (Tecnología) y CLI-3 80
 * (Hogar). Agosto: CLI-1 150 y CLI-2 100. Los perfiles 101, 102 y 103 son los de CLI-1, CLI-2 y CLI-3.
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ExportacionReportesIntegracionTest {

    private static final String COMPORTAMIENTO = "/api/comportamiento/reportes/comportamiento";
    private static final String COMPARATIVO = "/api/comportamiento/reportes/comparativo";
    private static final String PERMISOS_GERENTE = "CLIENTE_CONSULTAR,INDICADORES_CONSULTAR,SEGMENTOS_CONSULTAR,"
            + "REPORTES_CONSULTAR,REPORTES_EXPORTAR";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Las migraciones reales del Gateway son dueñas de la auditoría compartida. */
    @DynamicPropertySource
    static void auditoria(DynamicPropertyRegistry registro) {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("seguridad").locations("filesystem:../api-gateway/src/main/resources/db/migration").load()
                .migrate();
    }

    @MockBean
    private ClientePerfiles perfiles;

    @MockBean
    private ClientesDelSegmento clientesDelSegmento;

    @Autowired
    private IngestaCompras ingesta;

    @Autowired
    private EventoRecibidoRepository eventos;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE comportamiento.cliente_inactivo, comportamiento.frecuencia_compra, "
                + "comportamiento.intento_reproceso, comportamiento.evento_procesado, "
                + "comportamiento.anulacion_item, comportamiento.anulacion, "
                + "comportamiento.compra_item, comportamiento.compra, comportamiento.evento_recibido");
        when(perfiles.buscar(anyString())).thenReturn(Optional.empty());
        when(perfiles.buscar("CLI-1")).thenReturn(Optional.of(101L));
        when(perfiles.buscar("CLI-2")).thenReturn(Optional.of(102L));
        when(perfiles.buscar("CLI-3")).thenReturn(Optional.of(103L));
        when(clientesDelSegmento.idsDe("Alto valor")).thenReturn(List.of(101L, 102L));
        comprar("V-A1", "CLI-1", "2026-08-15T10:00:00-04:00", Map.of("Hogar", "150.00"));
        comprar("V-A2", "CLI-2", "2026-08-20T10:00:00-04:00", Map.of("Hogar", "100.00"));
        comprar("V-B1", "CLI-1", "2026-09-05T10:00:00-04:00", Map.of("Hogar", "400.00"));
        comprar("V-B2", "CLI-1", "2026-09-20T10:00:00-04:00", Map.of("Tecnología", "120.00", "Hogar", "80.00"));
        comprar("V-B3", "CLI-2", "2026-09-10T10:00:00-04:00", Map.of("Tecnología", "500.00"));
        comprar("V-B4", "CLI-3", "2026-09-12T10:00:00-04:00", Map.of("Hogar", "80.00"));
    }

    // --- Criterio 1: el archivo contiene los mismos datos que en pantalla ---

    @Test
    void elCsvDelReporteDeComportamientoTieneLosMismosDatosQueLaPantalla() throws Exception {
        JsonNode pantalla = exacto(gerente(septiembre(get(COMPORTAMIENTO))).andExpect(status().isOk()));
        String csv = gerente(septiembre(get(COMPORTAMIENTO + "/exportar")).param("formato", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"reporte-comportamiento_2026-09-01_2026-09-30.csv\""))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(csv).startsWith(EscrituraCsv.BOM + "Reporte de comportamiento de compra\r\n");
        List<String[]> filas = Arrays.stream(csv.split("\r\n")).map(linea -> linea.split(",", -1)).toList();
        assertThat(valor(filas, "COMPRAS")).isEqualTo(pantalla.at("/frecuencia/compras").asText());
        assertThat(valor(filas, "CLIENTES_CON_COMPRAS")).isEqualTo(pantalla.at("/frecuencia/clientesConCompras").asText());
        assertThat(valor(filas, "COMPRAS_POR_CLIENTE")).isEqualTo(texto(pantalla.at("/frecuencia/comprasPorCliente")));
        assertThat(valor(filas, "VALOR_TOTAL")).isEqualTo(texto(pantalla.at("/valor/total")));
        assertThat(valor(filas, "TICKET_PROMEDIO")).isEqualTo(texto(pantalla.at("/valor/ticketPromedio")));
        assertThat(valor(filas, "VALOR_POR_CLIENTE")).isEqualTo(texto(pantalla.at("/valor/valorPorCliente")));
        // Las categorías, en el mismo orden y con los mismos valores.
        int titulos = indice(filas, "Categoria");
        JsonNode categorias = pantalla.get("categorias");
        assertThat(categorias.size()).isPositive();
        for (int i = 0; i < categorias.size(); i++) {
            String[] fila = filas.get(titulos + 1 + i);
            JsonNode categoria = categorias.get(i);
            assertThat(fila[0]).isEqualTo(categoria.get("categoria").asText());
            assertThat(fila[1]).isEqualTo(categoria.get("compras").asText());
            assertThat(fila[2]).isEqualTo(categoria.get("unidades").asText());
            assertThat(fila[3]).isEqualTo(texto(categoria.get("monto")));
            assertThat(fila[4]).isEqualTo(texto(categoria.get("porcentaje")));
        }
    }

    @Test
    void elPdfDelReporteDeComportamientoMuestraLosMismosValoresQueLaPantalla() throws Exception {
        JsonNode pantalla = exacto(gerente(septiembre(get(COMPORTAMIENTO)).param("segmento", "Alto valor"))
                .andExpect(status().isOk()));
        byte[] pdf = gerente(septiembre(get(COMPORTAMIENTO + "/exportar")).param("formato", "PDF")
                        .param("segmento", "Alto valor"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"reporte-comportamiento_2026-09-01_2026-09-30_alto-valor.pdf\""))
                .andReturn().getResponse().getContentAsByteArray();

        String texto = ExportacionReportesPdfTest.texto(pdf).replaceAll("\\s+", "");
        assertThat(texto).contains("SegmentoAltovalor(2clientes)");
        assertThat(texto).contains("ComprasCOMPRAS" + pantalla.at("/frecuencia/compras").asText());
        assertThat(texto).contains("Valortotal(Bs)VALOR_TOTAL"
                + DocumentoPdf.numero(pantalla.at("/valor/total").decimalValue(), false));
        assertThat(texto).contains("Ticketpromedio(Bs)TICKET_PROMEDIO"
                + DocumentoPdf.numero(pantalla.at("/valor/ticketPromedio").decimalValue(), false));
        for (JsonNode categoria : pantalla.get("categorias")) {
            assertThat(texto).contains(categoria.get("categoria").asText().replace(" ", "")
                    + categoria.get("compras").asText() + categoria.get("unidades").asText()
                    + DocumentoPdf.numero(categoria.get("monto").decimalValue(), false));
        }
        assertThat(texto).contains("Generadoporluis");
    }

    @Test
    void elComparativoSeExportaEnLosDosFormatosConLosMismosValores() throws Exception {
        JsonNode pantalla = exacto(gerente(agostoContraSeptiembre(get(COMPARATIVO))).andExpect(status().isOk()));
        String csv = gerente(agostoContraSeptiembre(get(COMPARATIVO + "/exportar")).param("formato", "csv"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        byte[] pdf = gerente(agostoContraSeptiembre(get(COMPARATIVO + "/exportar")).param("formato", "pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; "
                        + "filename=\"reporte-comparativo_2026-08-01_2026-08-31_vs_2026-09-01_2026-09-30.pdf\""))
                .andReturn().getResponse().getContentAsByteArray();

        List<String[]> filas = Arrays.stream(csv.split("\r\n")).map(linea -> linea.split(",", -1)).toList();
        String textoPdf = ExportacionReportesPdfTest.texto(pdf).replaceAll("\\s+", "");
        for (JsonNode indicador : pantalla.get("indicadores")) {
            String codigo = indicador.get("codigo").asText();
            String[] fila = filas.stream().filter(f -> f.length > 1 && f[1].equals(codigo)).findFirst().orElseThrow();
            assertThat(fila[2]).isEqualTo(texto(indicador.get("periodoA")));
            assertThat(fila[3]).isEqualTo(texto(indicador.get("periodoB")));
            assertThat(fila[4]).isEqualTo(texto(indicador.get("variacionAbsoluta")));
            boolean conteo = codigo.equals("COMPRAS") || codigo.equals("CLIENTES_CON_COMPRAS");
            assertThat(textoPdf).contains(indicador.get("nombre").asText().replace(" ", "")
                    + DocumentoPdf.numero(indicador.get("periodoA").decimalValue(), conteo)
                    + DocumentoPdf.numero(indicador.get("periodoB").decimalValue(), conteo));
        }
    }

    @Test
    void unFormatoDesconocidoOUnPeriodoInvalidoResponden400SinExportarNada() throws Exception {
        long antes = exportaciones("luis");
        gerente(septiembre(get(COMPORTAMIENTO + "/exportar")).param("formato", "xlsx"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Formato de exportación inválido: 'xlsx' (se espera csv o pdf)"));
        gerente(septiembre(get(COMPORTAMIENTO + "/exportar")))
                .andExpect(status().isBadRequest());
        gerente(get(COMPORTAMIENTO + "/exportar").param("formato", "pdf")
                        .param("desde", "2026-09-30").param("hasta", "2026-09-01"))
                .andExpect(status().isBadRequest());
        gerente(get(COMPARATIVO + "/exportar").param("formato", "csv").param("desdeA", "2026-08-01")
                        .param("hastaA", "2026-09-10").param("desdeB", "2026-09-01").param("hastaB", "2026-09-30"))
                .andExpect(status().isBadRequest());
        assertThat(exportaciones("luis")).isEqualTo(antes);
    }

    // --- Criterio 2: un rol sin permiso de exportación no exporta ---

    @Test
    void unRolSinPermisoDeExportacionRecibe403AunqueConsulteElReporte() throws Exception {
        String soloConsulta = "INDICADORES_CONSULTAR,SEGMENTOS_CONSULTAR,REPORTES_CONSULTAR";
        long antes = exportaciones("marta");
        for (MockHttpServletRequestBuilder exportacion : List.of(
                septiembre(get(COMPORTAMIENTO + "/exportar")).param("formato", "csv"),
                septiembre(get(COMPORTAMIENTO + "/exportar")).param("formato", "pdf"),
                agostoContraSeptiembre(get(COMPARATIVO + "/exportar")).param("formato", "pdf"),
                agostoContraSeptiembre(get(COMPARATIVO + "/csv")))) {
            mvc.perform(exportacion.header("X-Usuario", "marta").header("X-Usuario-Permisos", soloConsulta))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.mensaje").value("Su rol no tiene permiso para exportar los reportes de clientes"));
        }
        // Ve el reporte en pantalla, pero no lo saca del CRM.
        mvc.perform(septiembre(get(COMPORTAMIENTO)).header("X-Usuario", "marta").header("X-Usuario-Permisos", soloConsulta))
                .andExpect(status().isOk());
        // El Agente de Atención no consulta ni exporta, aunque tuviera el permiso de exportar.
        mvc.perform(septiembre(get(COMPORTAMIENTO + "/exportar")).param("formato", "csv").header("X-Usuario", "ana")
                        .header("X-Usuario-Permisos", "CLIENTE_CONSULTAR,INDICADORES_CONSULTAR,REPORTES_EXPORTAR"))
                .andExpect(status().isForbidden());
        mvc.perform(septiembre(get(COMPORTAMIENTO + "/exportar")).param("formato", "csv"))
                .andExpect(status().isUnauthorized());
        assertThat(exportaciones("marta")).isEqualTo(antes);
    }

    // --- Criterio 3: la auditoría dice quién exportó, qué y cuándo ---

    @Test
    void cadaExportacionQuedaEnLaAuditoriaConQuienQueYCuando() throws Exception {
        long antes = exportaciones("luis");
        gerente(septiembre(get(COMPORTAMIENTO + "/exportar")).param("formato", "pdf").param("segmento", "Alto valor"))
                .andExpect(status().isOk());
        gerente(agostoContraSeptiembre(get(COMPARATIVO + "/exportar")).param("formato", "csv"))
                .andExpect(status().isOk());

        assertThat(exportaciones("luis")).isEqualTo(antes + 2);
        List<Map<String, Object>> registros = jdbc.queryForList("""
                SELECT entidad, entidad_id, detalle, ocurrido_en FROM auditoria.evento
                WHERE usuario = 'luis' AND operacion = 'REPORTE_EXPORTADO' ORDER BY id DESC LIMIT 2
                """);
        JsonNode comparativo = json.readTree((String) registros.get(0).get("detalle"));
        JsonNode comportamiento = json.readTree((String) registros.get(1).get("detalle"));

        assertThat(registros.get(0).get("entidad")).isEqualTo("REPORTE");
        assertThat(registros.get(0).get("entidad_id")).isEqualTo("COMPARATIVO_PERIODOS");
        assertThat(registros.get(0).get("ocurrido_en")).isNotNull();
        assertThat(comparativo.get("formato").asText()).isEqualTo("CSV");
        assertThat(comparativo.get("archivo").asText())
                .isEqualTo("reporte-comparativo_2026-08-01_2026-08-31_vs_2026-09-01_2026-09-30.csv");
        assertThat(comparativo.get("desdeA").asText()).isEqualTo("2026-08-01");
        assertThat(comparativo.get("hastaB").asText()).isEqualTo("2026-09-30");
        assertThat(comparativo.get("segmento").isNull()).isTrue();
        assertThat(comparativo.get("bytes").asLong()).isPositive();

        assertThat(registros.get(1).get("entidad_id")).isEqualTo("COMPORTAMIENTO_COMPRA");
        assertThat(comportamiento.get("formato").asText()).isEqualTo("PDF");
        assertThat(comportamiento.get("segmento").asText()).isEqualTo("Alto valor");
        assertThat(comportamiento.get("desde").asText()).isEqualTo("2026-09-01");
        assertThat(comportamiento.get("sinDatos").asBoolean()).isFalse();
        // Sin datos personales: ni clientes ni montos.
        assertThat(registros.get(1).get("detalle").toString()).doesNotContain("CLI-", "101", "400.00");
    }

    private long exportaciones(String usuario) {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria.evento WHERE usuario = ? AND operacion = ?",
                Long.class, usuario, AuditoriaExportaciones.OPERACION);
    }

    private static MockHttpServletRequestBuilder septiembre(MockHttpServletRequestBuilder solicitud) {
        return solicitud.param("desde", "2026-09-01").param("hasta", "2026-09-30");
    }

    private static MockHttpServletRequestBuilder agostoContraSeptiembre(MockHttpServletRequestBuilder solicitud) {
        return solicitud.param("desdeA", "2026-08-01").param("hastaA", "2026-08-31")
                .param("desdeB", "2026-09-01").param("hastaB", "2026-09-30");
    }

    private ResultActions gerente(MockHttpServletRequestBuilder solicitud) throws Exception {
        return mvc.perform(solicitud.header("X-Usuario", "luis").header("X-Usuario-Permisos", PERMISOS_GERENTE));
    }

    /** El JSON con los decimales exactos: leído como double, "50.00" se volvería "50.0". */
    private JsonNode exacto(ResultActions resultado) throws Exception {
        ObjectMapper exacto = json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
        return exacto.readTree(resultado.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** Un número del JSON tal como llegó, con su escala: vacío si es nulo. */
    private static String texto(JsonNode valor) {
        return valor == null || valor.isNull() ? "" : valor.decimalValue().toPlainString();
    }

    private static String valor(List<String[]> filas, String codigo) {
        return filas.stream().filter(f -> f.length == 3 && f[1].equals(codigo)).findFirst().orElseThrow()[2];
    }

    private static int indice(List<String[]> filas, String primeraColumna) {
        for (int i = 0; i < filas.size(); i++) {
            if (filas.get(i)[0].equals(primeraColumna)) {
                return i;
            }
        }
        throw new AssertionError("No está la fila " + primeraColumna);
    }

    private void comprar(String idCompra, String idCliente, String fecha, Map<String, String> items) {
        String detalle = String.join(", ", items.entrySet().stream()
                .map(i -> "{ \"categoria\": \"%s\", \"cantidad\": 1, \"monto\": %s }".formatted(i.getKey(), i.getValue()))
                .toList());
        String total = items.values().stream().map(java.math.BigDecimal::new)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add).toPlainString();
        ingesta.recibir("""
                {
                  "idEvento": "%s",
                  "tipoEvento": "COMPRA_CONFIRMADA",
                  "fechaEmision": "%s",
                  "compra": {
                    "idCompra": "%s",
                    "idCliente": "%s",
                    "fecha": "%s",
                    "montoTotal": %s,
                    "items": [ %s ]
                  }
                }
                """.formatted(UUID.randomUUID(), fecha, idCompra, idCliente, fecha, total, detalle));
        assertThat(eventos.findAll()).allMatch(e -> e.getEstado() == EstadoEvento.PROCESADO);
    }
}
