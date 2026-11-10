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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-501 · Reporte comparativo entre dos periodos (SCRUM-62) con PostgreSQL real y compras por la ingesta:
 * indicadores de ambos periodos y su variación (criterio 1), periodos superpuestos (criterio 2) y exportación
 * a CSV con los mismos valores (criterio 3). Agosto (A): CLI-1 150 y CLI-2 100. Septiembre (B): CLI-1 400 y 200,
 * CLI-2 500 y CLI-3 80. Los perfiles 101, 102 y 103 son los de CLI-1, CLI-2 y CLI-3.
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ReporteComparativoIntegracionTest {

    private static final String RUTA = "/api/comportamiento/reportes/comparativo";
    private static final String PERMISOS_GERENTE = "CLIENTE_CONSULTAR,INDICADORES_CONSULTAR,SEGMENTOS_CONSULTAR,"
            + "REPORTES_CONSULTAR,REPORTES_EXPORTAR";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /** La exportación se audita (SCRUM-484); las migraciones reales del Gateway son dueñas de la auditoría. */
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
        when(clientesDelSegmento.idsDe("Vacio")).thenReturn(List.of());
        comprar("V-A1", "CLI-1", "2026-08-15T10:00:00-04:00", "150.00");
        comprar("V-A2", "CLI-2", "2026-08-20T10:00:00-04:00", "100.00");
        comprar("V-B1", "CLI-1", "2026-09-05T10:00:00-04:00", "400.00");
        comprar("V-B2", "CLI-1", "2026-09-20T10:00:00-04:00", "200.00");
        comprar("V-B3", "CLI-2", "2026-09-10T10:00:00-04:00", "500.00");
        comprar("V-B4", "CLI-3", "2026-09-12T10:00:00-04:00", "80.00");
    }

    // --- Criterio 1: los indicadores de ambos periodos y su variación ---

    @Test
    void muestraLosIndicadoresDeAmbosPeriodosYSuVariacion() throws Exception {
        comparar(agostoContraSeptiembre(get(RUTA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodoA.desde").value("2026-08-01"))
                .andExpect(jsonPath("$.periodoB.hasta").value("2026-09-30"))
                .andExpect(jsonPath("$.alcance.segmento").value(nullValue()))
                .andExpect(jsonPath("$.sinDatos").value(false))
                .andExpect(jsonPath("$.indicadores[*].codigo", contains("COMPRAS", "CLIENTES_CON_COMPRAS",
                        "COMPRAS_POR_CLIENTE", "VALOR_TOTAL", "TICKET_PROMEDIO", "VALOR_POR_CLIENTE")))
                // Compras: 2 → 4, +2, +100 %.
                .andExpect(jsonPath("$.indicadores[0].periodoA").value(2))
                .andExpect(jsonPath("$.indicadores[0].periodoB").value(4))
                .andExpect(jsonPath("$.indicadores[0].variacionAbsoluta").value(2))
                .andExpect(jsonPath("$.indicadores[0].variacionPorcentual").value(100.00))
                .andExpect(jsonPath("$.indicadores[0].tendencia").value("CRECE"))
                // Clientes que compraron: 2 → 3, +50 %.
                .andExpect(jsonPath("$.indicadores[1].variacionPorcentual").value(50.00))
                // Valor total: 250 → 1180, +930, +372 %.
                .andExpect(jsonPath("$.indicadores[3].periodoA").value(250.00))
                .andExpect(jsonPath("$.indicadores[3].periodoB").value(1180.00))
                .andExpect(jsonPath("$.indicadores[3].variacionAbsoluta").value(930.00))
                .andExpect(jsonPath("$.indicadores[3].variacionPorcentual").value(372.00))
                // Ticket promedio: 125 → 295, +136 %.
                .andExpect(jsonPath("$.indicadores[4].periodoA").value(125.00))
                .andExpect(jsonPath("$.indicadores[4].periodoB").value(295.00))
                .andExpect(jsonPath("$.indicadores[4].variacionPorcentual").value(136.00));
    }

    @Test
    void cadaPeriodoCoincideConElReporteDeComportamientoDeEsePeriodo() throws Exception {
        JsonNode comparativo = leer(comparar(agostoContraSeptiembre(get(RUTA)).param("segmento", "Alto valor")));
        JsonNode agosto = leer(comparar(get("/api/comportamiento/reportes/comportamiento")
                .param("desde", "2026-08-01").param("hasta", "2026-08-31").param("segmento", "Alto valor")));
        JsonNode septiembre = leer(comparar(get("/api/comportamiento/reportes/comportamiento")
                .param("desde", "2026-09-01").param("hasta", "2026-09-30").param("segmento", "Alto valor")));

        for (String[] par : new String[][] {{"0", "/frecuencia/compras"}, {"1", "/frecuencia/clientesConCompras"},
                {"2", "/frecuencia/comprasPorCliente"}, {"3", "/valor/total"}, {"4", "/valor/ticketPromedio"},
                {"5", "/valor/valorPorCliente"}}) {
            JsonNode indicador = comparativo.at("/indicadores/" + par[0]);
            assertThat(indicador.get("periodoA").decimalValue()).isEqualByComparingTo(agosto.at(par[1]).decimalValue());
            assertThat(indicador.get("periodoB").decimalValue())
                    .isEqualByComparingTo(septiembre.at(par[1]).decimalValue());
        }
        assertThat(comparativo.at("/alcance/clientesDelSegmento").asLong()).isEqualTo(2);
    }

    @Test
    void sinComprasEnNingunPeriodoRespondeSinDatosYUnPromedioSinBaseNoTieneVariacion() throws Exception {
        comparar(get(RUTA).param("desdeA", "2026-01-01").param("hastaA", "2026-01-31")
                        .param("desdeB", "2026-02-01").param("hastaB", "2026-02-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinDatos").value(true))
                .andExpect(jsonPath("$.mensaje").value("No hay compras vigentes en ninguno de los dos periodos."));
        // Enero sin compras contra septiembre: el ticket de enero no existe, así que no hay variación.
        comparar(get(RUTA).param("desdeA", "2026-01-01").param("hastaA", "2026-01-31")
                        .param("desdeB", "2026-09-01").param("hastaB", "2026-09-30"))
                .andExpect(jsonPath("$.sinDatos").value(false))
                .andExpect(jsonPath("$.indicadores[0].tendencia").value("NUEVO"))
                .andExpect(jsonPath("$.indicadores[4].periodoA").value(nullValue()))
                .andExpect(jsonPath("$.indicadores[4].variacionAbsoluta").value(nullValue()))
                .andExpect(jsonPath("$.indicadores[4].tendencia").value(nullValue()));
        comparar(agostoContraSeptiembre(get(RUTA)).param("segmento", "Vacio"))
                .andExpect(jsonPath("$.mensaje").value("El segmento 'Vacio' no tiene clientes asignados."));
    }

    // --- Criterio 2: periodos superpuestos ---

    @Test
    void periodosSuperpuestosOIncompletosResponden400ConElMotivo() throws Exception {
        comparar(get(RUTA).param("desdeA", "2026-08-01").param("hastaA", "2026-09-10")
                        .param("desdeB", "2026-09-01").param("hastaB", "2026-09-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Los periodos a comparar no pueden superponerse: "
                        + "2026-08-01 a 2026-09-10 y 2026-09-01 a 2026-09-30"));
        comparar(get(RUTA + "/csv").param("desdeA", "2026-09-01").param("hastaA", "2026-09-30")
                        .param("desdeB", "2026-09-15").param("hastaB", "2026-10-15"))
                .andExpect(status().isBadRequest());
        comparar(get(RUTA).param("desdeA", "2026-08-01").param("desdeB", "2026-09-01").param("hastaB", "2026-09-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(startsWith("Falta una fecha del periodo A")));
    }

    // --- Criterio 3: al exportarlo conserva los mismos valores ---

    @Test
    void elCsvConservaLosMismosValoresQueElReporte() throws Exception {
        // Decimales exactos: leídos como double, "50.00" del JSON se volvería "50.0" y no se compararía lo enviado.
        ObjectMapper exacto = json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
        JsonNode reporte = exacto.readTree(comparar(agostoContraSeptiembre(get(RUTA)).param("segmento", "Alto valor"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        MvcResult exportado = comparar(agostoContraSeptiembre(get(RUTA + "/csv")).param("segmento", "Alto valor"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; "
                        + "filename=\"reporte-comparativo_2026-08-01_2026-08-31_vs_2026-09-01_2026-09-30.csv\""))
                .andReturn();
        String csv = exportado.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(csv).startsWith(ExportacionComparativoCsv.BOM);
        // SCRUM-484: la exportación queda en la auditoría con quién, qué y en qué formato.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria.evento WHERE usuario = 'luis' "
                + "AND operacion = 'REPORTE_EXPORTADO' AND entidad_id = 'COMPARATIVO_PERIODOS' "
                + "AND detalle LIKE '%\"formato\":\"CSV\"%'", Long.class)).isPositive();
        assertThat(csv).contains("Alcance,Segmento Alto valor\r\n");
        List<String[]> filas = Arrays.stream(csv.split("\r\n")).map(linea -> linea.split(",", -1)).toList();
        int cabecera = 0;
        while (!filas.get(cabecera)[0].equals("Indicador")) {
            cabecera++;
        }
        JsonNode indicadores = reporte.get("indicadores");
        assertThat(filas.size() - cabecera - 1).isEqualTo(indicadores.size());
        for (int i = 0; i < indicadores.size(); i++) {
            String[] fila = filas.get(cabecera + 1 + i);
            JsonNode indicador = indicadores.get(i);
            assertThat(fila[1]).isEqualTo(indicador.get("codigo").asText());
            assertThat(fila[2]).isEqualTo(texto(indicador.get("periodoA")));
            assertThat(fila[3]).isEqualTo(texto(indicador.get("periodoB")));
            assertThat(fila[4]).isEqualTo(texto(indicador.get("variacionAbsoluta")));
            assertThat(fila[5]).isEqualTo(texto(indicador.get("variacionPorcentual")));
            assertThat(fila[6]).isEqualTo(indicador.get("tendencia").isNull() ? "" : indicador.get("tendencia").asText());
        }
    }

    // --- SCRUM-500: permiso de los reportes ---

    @Test
    void elComparativoYSuExportacionExigenElPermisoDeReportes() throws Exception {
        for (String ruta : List.of(RUTA, RUTA + "/csv")) {
            mvc.perform(agostoContraSeptiembre(get(ruta)).header("X-Usuario", "ana")
                            .header("X-Usuario-Permisos", "CLIENTE_CONSULTAR,INDICADORES_CONSULTAR,PUNTOS_CONSULTAR"))
                    .andExpect(status().isForbidden());
            mvc.perform(agostoContraSeptiembre(get(ruta))).andExpect(status().isUnauthorized());
        }
        // SCRUM-482: el CSV es una exportación (SCRUM-60) y exige además REPORTES_EXPORTAR.
        mvc.perform(agostoContraSeptiembre(get(RUTA + "/csv")).header("X-Usuario", "luis")
                        .header("X-Usuario-Permisos", "INDICADORES_CONSULTAR,REPORTES_CONSULTAR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Su rol no tiene permiso para exportar los reportes de clientes"));
    }

    /** Un número del JSON tal como llegó, con su escala: vacío si es nulo. */
    private static String texto(JsonNode valor) {
        return valor.isNull() ? "" : valor.decimalValue().toPlainString();
    }

    private static MockHttpServletRequestBuilder agostoContraSeptiembre(MockHttpServletRequestBuilder solicitud) {
        return solicitud.param("desdeA", "2026-08-01").param("hastaA", "2026-08-31")
                .param("desdeB", "2026-09-01").param("hastaB", "2026-09-30");
    }

    private ResultActions comparar(MockHttpServletRequestBuilder solicitud) throws Exception {
        return mvc.perform(solicitud.header("X-Usuario", "luis").header("X-Usuario-Permisos", PERMISOS_GERENTE));
    }

    private JsonNode leer(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
    }

    private void comprar(String idCompra, String idCliente, String fecha, String monto) {
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
                    "items": [ { "categoria": "Hogar", "cantidad": 1, "monto": %s } ]
                  }
                }
                """.formatted(UUID.randomUUID(), fecha, idCompra, idCliente, fecha, monto, monto));
        assertThat(eventos.findAll()).allMatch(e -> e.getEstado() == EstadoEvento.PROCESADO);
    }
}
