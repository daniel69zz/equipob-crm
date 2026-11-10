package com.maxiconecta.crm.comportamiento.reporte;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxiconecta.crm.comportamiento.comparacion.ClientesDelSegmento;
import com.maxiconecta.crm.comportamiento.compra.AgregadoConsumoCategorias;
import com.maxiconecta.crm.comportamiento.compra.AgregadoConsumoCategorias.ConsumoEnRango;
import com.maxiconecta.crm.comportamiento.compra.ClientePerfiles;
import com.maxiconecta.crm.comportamiento.compra.Identificador;
import com.maxiconecta.crm.comportamiento.indicador.CategoriaConsumida;
import com.maxiconecta.crm.comportamiento.indicador.RankingCategorias;
import com.maxiconecta.crm.comportamiento.ingesta.EstadoEvento;
import com.maxiconecta.crm.comportamiento.ingesta.EventoRecibidoRepository;
import com.maxiconecta.crm.comportamiento.ingesta.IngestaCompras;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-470 · Reporte de comportamiento de compra (SCRUM-58) con PostgreSQL real: frecuencia, valor y
 * categorías de un segmento en un periodo (criterio 1), la cartera completa, anulaciones y
 * devoluciones, y la coincidencia con los indicadores del cliente (criterio 3). Las compras entran por
 * la ingesta real, así que el contador de frecuencia del indicador (SCRUM-37) se actualiza solo. Los
 * perfiles 101, 102 y 103 son los de CLI-1, CLI-2 y CLI-3; CLI-SIN queda pendiente de vinculación.
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ReporteComportamientoIntegracionTest {

    private static final String RUTA = "/api/comportamiento/reportes/comportamiento";
    private static final String PERMISOS_GERENTE = "CLIENTE_CONSULTAR,INDICADORES_CONSULTAR,SEGMENTOS_CONSULTAR,"
            + "REPORTES_CONSULTAR";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private ClientePerfiles perfiles;

    @MockBean
    private ClientesDelSegmento clientesDelSegmento;

    @Autowired
    private IngestaCompras ingesta;

    @Autowired
    private EventoRecibidoRepository eventos;

    @Autowired
    private AgregadoConsumoCategorias consumoCategorias;

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
    }

    // --- Criterio 1: frecuencia, valor y categorías del grupo en el periodo ---

    @Test
    void muestraFrecuenciaValorYCategoriasDeLosClientesDelSegmentoEnElPeriodo() throws Exception {
        comprarConDosItems("V-1", "CLI-1", "2026-09-05T10:00:00-04:00", "300.00", "100.00");
        comprar("V-2", "CLI-1", "2026-09-20T10:00:00-04:00", "Hogar", "200.00");
        comprar("V-3", "CLI-2", "2026-09-10T10:00:00-04:00", "Hogar", "500.00");
        comprar("V-OTRO-SEGMENTO", "CLI-3", "2026-09-10T10:00:00-04:00", "Hogar", "9999.00");
        comprar("V-FUERA-PERIODO", "CLI-1", "2026-10-02T10:00:00-04:00", "Hogar", "9999.00");

        reporte(get(RUTA).param("desde", "2026-09-01").param("hasta", "2026-09-30").param("segmento", "Alto valor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alcance.segmento").value("Alto valor"))
                .andExpect(jsonPath("$.alcance.clientesDelSegmento").value(2))
                .andExpect(jsonPath("$.desde").value("2026-09-01"))
                .andExpect(jsonPath("$.hasta").value("2026-09-30"))
                .andExpect(jsonPath("$.sinDatos").value(false))
                // Frecuencia: 3 compras de 2 clientes.
                .andExpect(jsonPath("$.frecuencia.compras").value(3))
                .andExpect(jsonPath("$.frecuencia.clientesConCompras").value(2))
                .andExpect(jsonPath("$.frecuencia.comprasPorCliente").value(1.50))
                // Valor: 400 + 200 + 500 = 1100; ticket 1100 / 3 = 366.67; por cliente 550.
                .andExpect(jsonPath("$.valor.total").value(1100.00))
                .andExpect(jsonPath("$.valor.ticketPromedio").value(366.67))
                .andExpect(jsonPath("$.valor.valorPorCliente").value(550.00))
                // Categorías por monto: Hogar 700, Electrónica 300, Accesorios 100.
                .andExpect(jsonPath("$.categorias[*].categoria", contains("Hogar", "Electrónica", "Accesorios")))
                .andExpect(jsonPath("$.categorias[0].compras").value(2))
                .andExpect(jsonPath("$.categorias[0].monto").value(700.00))
                .andExpect(jsonPath("$.categorias[0].porcentaje").value(63.64))
                .andExpect(jsonPath("$.categorias[2].unidades").value(2));
    }

    @Test
    void unaCompraAnuladaNoCuentaYUnaDevolucionParcialDescuentaLoDevuelto() throws Exception {
        comprar("V-1", "CLI-1", "2026-09-05T10:00:00-04:00", "Hogar", "100.00");
        comprar("V-2", "CLI-1", "2026-09-06T10:00:00-04:00", "Hogar", "400.00");
        anularTotal("V-2", "CLI-1", "400.00", "2026-09-07T10:00:00-04:00");
        comprarConDosItems("V-3", "CLI-2", "2026-09-08T10:00:00-04:00", "300.00", "100.00");
        devolverAccesorios("V-3", "CLI-2", "100.00", "2026-09-09T10:00:00-04:00");

        reporte(get(RUTA).param("desde", "2026-09-01").param("hasta", "2026-09-30").param("segmento", "Alto valor"))
                .andExpect(jsonPath("$.frecuencia.compras").value(2))
                .andExpect(jsonPath("$.valor.total").value(400.00))
                // Accesorios se devolvió completo: desaparece del ranking.
                .andExpect(jsonPath("$.categorias[*].categoria", contains("Electrónica", "Hogar")));
    }

    @Test
    void sinSegmentoCubreLaCarteraYDejaFueraLasComprasSinVincular() throws Exception {
        comprar("V-1", "CLI-1", "2026-09-05T10:00:00-04:00", "Hogar", "100.00");
        comprar("V-2", "CLI-3", "2026-09-06T10:00:00-04:00", "Hogar", "200.00");
        comprar("V-PENDIENTE", "CLI-SIN", "2026-09-07T10:00:00-04:00", "Hogar", "9999.00");

        reporte(get(RUTA).param("desde", "2026-09-01").param("hasta", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alcance.segmento").value(nullValue()))
                .andExpect(jsonPath("$.alcance.clientesDelSegmento").value(nullValue()))
                .andExpect(jsonPath("$.frecuencia.compras").value(2))
                .andExpect(jsonPath("$.frecuencia.clientesConCompras").value(2))
                .andExpect(jsonPath("$.valor.total").value(300.00));
    }

    @Test
    void unSegmentoSinClientesOUnPeriodoSinComprasRespondeSinDatosYNoEsUnError() throws Exception {
        comprar("V-1", "CLI-1", "2026-09-05T10:00:00-04:00", "Hogar", "100.00");

        reporte(get(RUTA).param("desde", "2026-09-01").param("hasta", "2026-09-30").param("segmento", "Vacio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinDatos").value(true))
                .andExpect(jsonPath("$.mensaje").value("El segmento 'Vacio' no tiene clientes asignados."))
                .andExpect(jsonPath("$.alcance.clientesDelSegmento").value(0));
        reporte(get(RUTA).param("desde", "2026-01-01").param("hasta", "2026-01-31").param("segmento", "Alto valor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinDatos").value(true))
                .andExpect(jsonPath("$.frecuencia.compras").value(0))
                .andExpect(jsonPath("$.valor.total").value(0))
                .andExpect(jsonPath("$.valor.ticketPromedio").value(nullValue()))
                .andExpect(jsonPath("$.categorias", hasSize(0)));
    }

    @Test
    void unPeriodoInvertidoOIncompletoResponde400ConElMotivo() throws Exception {
        reporte(get(RUTA).param("desde", "2026-09-30").param("hasta", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje")
                        .value("La fecha 'desde' (2026-09-30) no puede ser posterior a 'hasta' (2026-09-01)"));
        reporte(get(RUTA).param("hasta", "2026-09-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Falta la fecha 'desde' del periodo (formato AAAA-MM-DD)"));
    }

    // --- Criterio 3: los resultados coinciden con los indicadores del cliente ---

    @Test
    void elReporteDeUnClienteCoincideConSusIndicadoresYSuRankingDeCategorias() throws Exception {
        when(clientesDelSegmento.idsDe("Solo CLI-1")).thenReturn(List.of(101L));
        comprarConDosItems("V-1", "CLI-1", "2025-03-05T10:00:00-04:00", "300.00", "100.00");
        comprar("V-2", "CLI-1", "2026-01-20T10:00:00-04:00", "Hogar", "250.00");
        comprar("V-3", "CLI-1", "2026-09-10T10:00:00-04:00", "Deportes", "80.00");
        comprar("V-4", "CLI-1", "2026-09-11T10:00:00-04:00", "Hogar", "500.00");
        anularTotal("V-4", "CLI-1", "500.00", "2026-09-12T10:00:00-04:00");
        devolverAccesorios("V-1", "CLI-1", "100.00", "2025-03-09T10:00:00-04:00");

        // Un periodo que abarca todo el historial del cliente.
        JsonNode reporte = leer(reporte(get(RUTA).param("desde", "2025-01-01").param("hasta", "2026-12-31")
                .param("segmento", "Solo CLI-1")));
        JsonNode indicadores = leer(mvc.perform(get("/api/comportamiento/clientes/101/indicadores")
                .param("identificador", "CLI-1")));

        assertThat(reporte.at("/frecuencia/compras").asLong()).isEqualTo(indicadores.at("/frecuencia").asLong())
                .isEqualTo(3);
        assertThat(reporte.at("/valor/total").decimalValue())
                .isEqualByComparingTo(indicadores.at("/valorAcumulado/valor").decimalValue())
                .isEqualByComparingTo("630.00");
        assertThat(reporte.at("/valor/ticketPromedio").decimalValue())
                .isEqualByComparingTo(indicadores.at("/ticketPromedio/valor").decimalValue());

        ConsumoEnRango historial = consumoCategorias.consumirTodo(List.of(Identificador.parsear("CLI-1")));
        List<CategoriaConsumida> ranking = RankingCategorias.calcular(historial.compradas(), historial.devueltas());
        assertThat(reporte.at("/categorias").findValuesAsText("categoria"))
                .containsExactlyElementsOf(ranking.stream().map(CategoriaConsumida::categoria).toList());
        for (int i = 0; i < ranking.size(); i++) {
            assertThat(reporte.at("/categorias/" + i + "/monto").decimalValue())
                    .isEqualByComparingTo(ranking.get(i).monto());
            assertThat(reporte.at("/categorias/" + i + "/compras").asLong()).isEqualTo(ranking.get(i).compras());
        }
    }

    @Test
    void unaCompraNuevaSeReflejaEnElReporteSiguiente() throws Exception {
        MockHttpServletRequestBuilder consulta = get(RUTA).param("desde", "2026-09-01").param("hasta", "2026-09-30")
                .param("segmento", "Alto valor");
        comprar("V-1", "CLI-1", "2026-09-05T10:00:00-04:00", "Hogar", "100.00");
        reporte(consulta).andExpect(jsonPath("$.frecuencia.compras").value(1));

        comprar("V-2", "CLI-2", "2026-09-06T10:00:00-04:00", "Hogar", "50.00");
        reporte(get(RUTA).param("desde", "2026-09-01").param("hasta", "2026-09-30").param("segmento", "Alto valor"))
                .andExpect(jsonPath("$.frecuencia.compras").value(2))
                .andExpect(jsonPath("$.valor.total").value(150.00));
    }

    // --- Permiso propio de los reportes ---

    @Test
    void sinElPermisoDeReportesRecibe403YSinIdentidad401() throws Exception {
        mvc.perform(get(RUTA).param("desde", "2026-09-01").param("hasta", "2026-09-30")
                        .header("X-Usuario", "ana")
                        .header("X-Usuario-Permisos", "CLIENTE_CONSULTAR,INDICADORES_CONSULTAR,PUNTOS_CONSULTAR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Su rol no tiene permiso para consultar los reportes de clientes"));
        mvc.perform(get(RUTA).param("desde", "2026-09-01").param("hasta", "2026-09-30"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Se requiere la identidad del Gateway"));
    }

    private ResultActions reporte(MockHttpServletRequestBuilder solicitud) throws Exception {
        return mvc.perform(solicitud.header("X-Usuario", "luis").header("X-Usuario-Permisos", PERMISOS_GERENTE));
    }

    private JsonNode leer(ResultActions resultado) throws Exception {
        // JSON es UTF-8; sin charset en la respuesta MockMvc decodificaría en ISO-8859-1 ("Electrónica").
        return json.readTree(resultado.andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
    }

    private void comprar(String idCompra, String idCliente, String fecha, String categoria, String monto) {
        ingesta.recibir(compra(idCompra, idCliente, fecha, monto,
                "{ \"categoria\": \"" + categoria + "\", \"cantidad\": 1, \"monto\": " + monto + " }"));
        exigirProcesados();
    }

    private void comprarConDosItems(String idCompra, String idCliente, String fecha, String montoElectronica,
                                    String montoAccesorios) {
        String total = new BigDecimal(montoElectronica).add(new BigDecimal(montoAccesorios)).toPlainString();
        ingesta.recibir(compra(idCompra, idCliente, fecha, total,
                "{ \"categoria\": \"Electrónica\", \"cantidad\": 1, \"monto\": " + montoElectronica + " }, "
                        + "{ \"categoria\": \"Accesorios\", \"cantidad\": 2, \"monto\": " + montoAccesorios + " }"));
        exigirProcesados();
    }

    private void anularTotal(String idCompra, String idCliente, String montoRevertido, String fecha) {
        ingesta.recibirAnulacion(anulacion(idCompra + "-A", idCompra, idCliente, "TOTAL", montoRevertido, fecha, ""));
        exigirProcesados();
    }

    private void devolverAccesorios(String idCompra, String idCliente, String monto, String fecha) {
        String items = ", \"items\": [ { \"categoria\": \"Accesorios\", \"cantidad\": 2, \"monto\": " + monto + " } ]";
        ingesta.recibirAnulacion(anulacion(idCompra + "-D", idCompra, idCliente, "PARCIAL", monto, fecha, items));
        exigirProcesados();
    }

    private void exigirProcesados() {
        assertThat(eventos.findAll()).allMatch(e -> e.getEstado() == EstadoEvento.PROCESADO);
    }

    private static String compra(String idCompra, String idCliente, String fecha, String montoTotal, String items) {
        return """
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
                """.formatted(UUID.randomUUID(), fecha, idCompra, idCliente, fecha, montoTotal, items);
    }

    private static String anulacion(String idAnulacion, String idCompra, String idCliente, String tipo,
                                    String montoRevertido, String fecha, String items) {
        return """
                {
                  "idEvento": "%s",
                  "tipoEvento": "COMPRA_ANULADA",
                  "fechaEmision": "%s",
                  "anulacion": {
                    "idAnulacion": "%s",
                    "idCompra": "%s",
                    "idCliente": "%s",
                    "tipo": "%s",
                    "fecha": "%s",
                    "montoRevertido": %s,
                    "motivo": "Prueba del reporte de comportamiento"%s
                  }
                }
                """.formatted(UUID.randomUUID(), fecha, idAnulacion, idCompra, idCliente, tipo, fecha,
                montoRevertido, items);
    }
}
