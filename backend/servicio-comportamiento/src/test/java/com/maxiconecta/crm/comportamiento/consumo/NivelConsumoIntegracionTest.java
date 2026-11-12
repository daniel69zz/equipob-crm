package com.maxiconecta.crm.comportamiento.consumo;

import com.maxiconecta.crm.comportamiento.compra.ClientePerfiles;
import com.maxiconecta.crm.comportamiento.ingesta.EstadoEvento;
import com.maxiconecta.crm.comportamiento.ingesta.EventoRecibidoRepository;
import com.maxiconecta.crm.comportamiento.ingesta.IngestaCompras;
import com.maxiconecta.crm.comportamiento.parametro.CambioParametro;
import com.maxiconecta.crm.comportamiento.parametro.CodigosParametros;
import com.maxiconecta.crm.comportamiento.parametro.Parametro;
import com.maxiconecta.crm.comportamiento.parametro.ParametroInvalidoException;
import com.maxiconecta.crm.comportamiento.parametro.ParametrosGenerales;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-272 · Nivel de consumo del cliente (SCRUM-27, docs/compra/nivel-consumo.md) con PostgreSQL
 * real: asignación con compras nuevas, cambio de nivel al cruzar un umbral (también hacia abajo con
 * una anulación o una devolución), valores exactamente en cada umbral, cliente sin compras vigentes
 * y listado por nivel. Umbrales por defecto: 500 (medio) y 5000 (alto).
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class NivelConsumoIntegracionTest {

    private static final String RUTA_INDICADORES = "/api/comportamiento/clientes/42/indicadores";
    private static final String RUTA_LISTADO = "/api/comportamiento/clientes";
    private static final String FECHA_COMPRA = "2026-09-20T10:00:00Z";
    private static final String FECHA_ANULACION = "2026-09-25T10:00:00Z";
    private static final Instant REFERENCIA = Instant.parse("2026-09-29T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private ClientePerfiles perfiles;

    @MockBean
    private Clock reloj;

    @Autowired
    private IngestaCompras ingesta;

    @Autowired
    private EventoRecibidoRepository eventos;

    @Autowired
    private ClasificadorConsumo clasificador;

    @Autowired
    private ClienteNivelConsumoRepository niveles;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ParametrosGenerales parametros;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE comportamiento.cliente_nivel_consumo, comportamiento.cliente_inactivo, "
                + "comportamiento.frecuencia_compra, comportamiento.intento_reproceso, "
                + "comportamiento.evento_procesado, comportamiento.anulacion_item, comportamiento.anulacion, "
                + "comportamiento.compra_item, comportamiento.compra, comportamiento.evento_recibido");
        when(perfiles.buscar(anyString())).thenReturn(Optional.empty());
        when(reloj.instant()).thenReturn(REFERENCIA);
        when(reloj.getZone()).thenReturn(ZoneOffset.UTC);
        cambiarUmbrales("500.00", "5000.00");
    }

    /** Fija los umbrales como lo haría el Administrador de CRM (SCRUM-518); el alto primero si sube. */
    private void cambiarUmbrales(String medio, String alto) {
        Map<String, String> nuevos = Map.of(CodigosParametros.NIVEL_CONSUMO_UMBRAL_MEDIO, medio,
                CodigosParametros.NIVEL_CONSUMO_UMBRAL_ALTO, alto);
        List<CambioParametro> cambios = new ArrayList<>();
        for (Parametro p : parametros.listar()) {
            String valor = nuevos.get(p.codigo());
            if (valor != null && p.valor().compareTo(new BigDecimal(valor)) != 0) {
                cambios.add(new CambioParametro(p.codigo(), new BigDecimal(valor), p.version()));
            }
        }
        if (!cambios.isEmpty()) {
            parametros.actualizar(cambios, "admin");
        }
    }

    // --- Criterio 1: se asigna el nivel cuyo umbral corresponde al valor acumulado ---

    @Test
    void unaCompraNuevaClasificaAlClienteConElNivelDeSuValor() throws Exception {
        comprar("V-1", "CLI-1", "350.50");

        mvc.perform(get(RUTA_INDICADORES).param("identificador", "CLI-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivelConsumo.nivel").value("BAJO"))
                .andExpect(jsonPath("$.nivelConsumo.valorAcumulado").value(350.50))
                .andExpect(jsonPath("$.nivelConsumo.sinNivel").value(false));
        assertThat(nivelPersistido("CLI-1")).isEqualTo("BAJO");
    }

    @Test
    void losValoresExactamenteEnCadaUmbralPertenecenAlNivelSuperior() {
        comprar("V-A", "CLI-499", "499.99");
        comprar("V-B", "CLI-500", "500.00");
        comprar("V-C", "CLI-4999", "4999.99");
        comprar("V-D", "CLI-5000", "5000.00");

        assertThat(nivelPersistido("CLI-499")).isEqualTo("BAJO");
        assertThat(nivelPersistido("CLI-500")).isEqualTo("MEDIO");
        assertThat(nivelPersistido("CLI-4999")).isEqualTo("MEDIO");
        assertThat(nivelPersistido("CLI-5000")).isEqualTo("ALTO");
    }

    // --- SCRUM-518: los umbrales son parámetros generales y rigen desde que se guardan ---

    @Test
    void alCambiarLosUmbralesSeReclasificaATodosLosClientesDeInmediato() throws Exception {
        comprar("V-U1", "CLI-600", "600.00");
        comprar("V-U2", "CLI-3000", "3000.00");
        comprar("V-U3", "CLI-6000", "6000.00");
        assertThat(nivelPersistido("CLI-600")).isEqualTo("MEDIO");
        assertThat(nivelPersistido("CLI-3000")).isEqualTo("MEDIO");

        cambiarUmbrales("1000.00", "2500.00");

        assertThat(nivelPersistido("CLI-600")).isEqualTo("BAJO");
        assertThat(nivelPersistido("CLI-3000")).isEqualTo("ALTO");
        assertThat(nivelPersistido("CLI-6000")).isEqualTo("ALTO");
        mvc.perform(get(RUTA_LISTADO).param("nivelConsumo", "ALTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.umbrales.medio").value(1000.00))
                .andExpect(jsonPath("$.umbrales.alto").value(2500.00));
        mvc.perform(get(RUTA_INDICADORES).param("identificador", "CLI-600"))
                .andExpect(jsonPath("$.nivelConsumo.nivel").value("BAJO"));
    }

    @Test
    void unUmbralAltoQueNoSuperaAlMedioSeRechazaSinCambiarNada() {
        Parametro alto = parametros.listar().stream()
                .filter(p -> p.codigo().equals(CodigosParametros.NIVEL_CONSUMO_UMBRAL_ALTO)).findFirst().orElseThrow();

        assertThatThrownBy(() -> parametros.actualizar(List.of(new CambioParametro(
                CodigosParametros.NIVEL_CONSUMO_UMBRAL_ALTO, new BigDecimal("500.00"), alto.version())), "admin"))
                .isInstanceOfSatisfying(ParametroInvalidoException.class, e -> assertThat(e.campos())
                        .containsEntry(CodigosParametros.NIVEL_CONSUMO_UMBRAL_ALTO,
                                "debe ser mayor que el umbral de consumo medio (500.00 Bs)"));
        assertThat(parametros.valor(CodigosParametros.NIVEL_CONSUMO_UMBRAL_ALTO)).isEqualByComparingTo("5000");
    }

    // --- Criterio 2: una compra nueva o una anulación actualiza el nivel si cruza un umbral ---

    @Test
    void unaCompraNuevaQueCruzaUnUmbralSubeElNivel() throws Exception {
        comprar("V-1", "CLI-1", "300.00");
        assertThat(nivelPersistido("CLI-1")).isEqualTo("BAJO");

        comprar("V-2", "CLI-1", "200.00");
        assertThat(nivelPersistido("CLI-1")).isEqualTo("MEDIO");

        comprar("V-3", "CLI-1", "4500.00");
        assertThat(nivelPersistido("CLI-1")).isEqualTo("ALTO");
        mvc.perform(get(RUTA_INDICADORES).param("identificador", "CLI-1"))
                .andExpect(jsonPath("$.nivelConsumo.nivel").value("ALTO"))
                .andExpect(jsonPath("$.nivelConsumo.valorAcumulado").value(5000.00));
    }

    @Test
    void anularUnaCompraQueBajaDelUmbralBajaElNivel() throws Exception {
        comprar("V-1", "CLI-1", "4600.00");
        comprar("V-2", "CLI-1", "400.00");
        assertThat(nivelPersistido("CLI-1")).isEqualTo("ALTO");

        anularTotal("V-2", "CLI-1", "400.00");

        assertThat(nivelPersistido("CLI-1")).isEqualTo("MEDIO");
        mvc.perform(get(RUTA_INDICADORES).param("identificador", "CLI-1"))
                .andExpect(jsonPath("$.nivelConsumo.nivel").value("MEDIO"))
                .andExpect(jsonPath("$.nivelConsumo.valorAcumulado").value(4600.00));
    }

    @Test
    void unaDevolucionParcialTambienBajaElNivelSiCruzaUnUmbral() {
        comprarConDosItems("V-1", "CLI-1", "4800.00", "400.00");
        assertThat(nivelPersistido("CLI-1")).isEqualTo("ALTO");

        devolverParcial("V-1", "CLI-1", "Accesorios", "400.00");

        assertThat(nivelPersistido("CLI-1")).isEqualTo("MEDIO");
        assertThat(valorPersistido("CLI-1")).isEqualByComparingTo("4800.00");
    }

    @Test
    void unaDevolucionQueNoCruzaNingunUmbralConservaElNivel() {
        comprarConDosItems("V-1", "CLI-1", "4900.00", "200.00");
        assertThat(nivelPersistido("CLI-1")).isEqualTo("ALTO");

        devolverParcial("V-1", "CLI-1", "Accesorios", "100.00");

        assertThat(nivelPersistido("CLI-1")).isEqualTo("ALTO");
        assertThat(valorPersistido("CLI-1")).isEqualByComparingTo("5000.00");
    }

    // --- Criterio 3: sin compras vigentes queda "sin nivel" y no es un error ---

    @Test
    void anularLaUnicaCompraDejaAlClienteSinNivel() throws Exception {
        comprar("V-1", "CLI-1", "800.00");
        assertThat(nivelPersistido("CLI-1")).isEqualTo("MEDIO");

        anularTotal("V-1", "CLI-1", "800.00");

        assertThat(nivelPersistido("CLI-1")).isNull();
        mvc.perform(get(RUTA_INDICADORES).param("identificador", "CLI-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivelConsumo.nivel").doesNotExist())
                .andExpect(jsonPath("$.nivelConsumo.valorAcumulado").value(0))
                .andExpect(jsonPath("$.nivelConsumo.sinNivel").value(true));
    }

    @Test
    void unClienteSinComprasNoEsUnErrorYQuedaSinNivel() throws Exception {
        mvc.perform(get(RUTA_INDICADORES).param("identificador", "CLI-NUNCA-COMPRO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivelConsumo.sinNivel").value(true));
        mvc.perform(get(RUTA_INDICADORES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivelConsumo.sinNivel").value(true));
    }

    // --- Un cliente con perfiles unificados: el nivel de la consulta usa el conjunto de identificadores ---

    @Test
    void elNivelDeLaConsultaSumaLosIdentificadoresDelClienteUnificado() throws Exception {
        comprar("V-1", "CLI-1", "300.00");
        comprar("V-2", "mp-user-1", "250.00");

        mvc.perform(get(RUTA_INDICADORES).param("identificador", "CLI-1", "mp-user-1"))
                .andExpect(jsonPath("$.nivelConsumo.nivel").value("MEDIO"))
                .andExpect(jsonPath("$.nivelConsumo.valorAcumulado").value(550.00));
        assertThat(nivelPersistido("CLI-1")).isEqualTo("BAJO");
        assertThat(nivelPersistido("mp-user-1")).isEqualTo("BAJO");
    }

    // --- Listado de clientes por nivel ---

    @Test
    void elListadoDevuelveSoloLosClientesDelNivelDelQueMasAportaAlQueMenos() throws Exception {
        comprar("V-1", "CLI-A", "6000.00");
        comprar("V-2", "CLI-B", "9000.00");
        comprar("V-3", "CLI-C", "5000.00");
        comprar("V-4", "CLI-D", "800.00");

        mvc.perform(get(RUTA_LISTADO).param("nivelConsumo", "ALTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.umbrales.medio").value(500))
                .andExpect(jsonPath("$.umbrales.alto").value(5000))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.content[0].idClienteOrigen").value("CLI-B"))
                .andExpect(jsonPath("$.content[0].nivel").value("ALTO"))
                .andExpect(jsonPath("$.content[0].valorAcumulado").value(9000.00))
                .andExpect(jsonPath("$.content[1].idClienteOrigen").value("CLI-A"))
                .andExpect(jsonPath("$.content[2].idClienteOrigen").value("CLI-C"));
        mvc.perform(get(RUTA_LISTADO).param("nivelConsumo", "medio"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].idClienteOrigen").value("CLI-D"));
        mvc.perform(get(RUTA_LISTADO).param("nivelConsumo", "BAJO"))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void elListadoSePaginaYUnClienteAnuladoDejaDeFigurar() throws Exception {
        comprar("V-1", "CLI-A", "100.00");
        comprar("V-2", "CLI-B", "200.00");
        comprar("V-3", "CLI-C", "300.00");
        anularTotal("V-2", "CLI-B", "200.00");

        mvc.perform(get(RUTA_LISTADO).param("nivelConsumo", "BAJO").param("tamanio", "1").param("pagina", "1"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.tamanio").value(1))
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.content[0].idClienteOrigen").value("CLI-A"));
    }

    @Test
    void unNivelInvalidoSeRechazaConUn400() throws Exception {
        mvc.perform(get(RUTA_LISTADO).param("nivelConsumo", "PLATINO"))
                .andExpect(status().isBadRequest());
    }

    // --- Ejecución programada: recalcula a todos y corrige lo que se haya desviado ---

    @Test
    void laEjecucionProgramadaCorrigeLoPersistidoSegunLasCompras() {
        comprar("V-1", "CLI-1", "800.00");
        comprar("V-2", "CLI-2", "100.00");
        jdbc.update("UPDATE comportamiento.cliente_nivel_consumo SET nivel = 'ALTO' WHERE id_cliente_origen = 'CLI-1'");
        jdbc.update("DELETE FROM comportamiento.cliente_nivel_consumo WHERE id_cliente_origen = 'CLI-2'");
        jdbc.update("INSERT INTO comportamiento.cliente_nivel_consumo (id_cliente_origen, nivel, valor_acumulado) "
                + "VALUES ('CLI-SIN-COMPRAS', 'MEDIO', 900)");

        int clasificados = clasificador.clasificarTodos();

        assertThat(clasificados).isEqualTo(2);
        assertThat(nivelPersistido("CLI-1")).isEqualTo("MEDIO");
        assertThat(nivelPersistido("CLI-2")).isEqualTo("BAJO");
        assertThat(nivelPersistido("CLI-SIN-COMPRAS")).isNull();
        assertThat(niveles.count()).isEqualTo(2);
    }

    // --- Utilidades ---

    private void comprar(String idCompra, String idCliente, String monto) {
        ingesta.recibir(compra(idCompra, idCliente, monto, "{ \"categoria\": \"Electrónica\", \"cantidad\": 1, "
                + "\"monto\": " + monto + " }"));
        exigirProcesados();
    }

    private void comprarConDosItems(String idCompra, String idCliente, String montoElectronica, String montoAccesorios) {
        BigDecimal total = new BigDecimal(montoElectronica).add(new BigDecimal(montoAccesorios));
        ingesta.recibir(compra(idCompra, idCliente, total.toPlainString(),
                "{ \"categoria\": \"Electrónica\", \"cantidad\": 1, \"monto\": " + montoElectronica + " }, "
                        + "{ \"categoria\": \"Accesorios\", \"cantidad\": 2, \"monto\": " + montoAccesorios + " }"));
        exigirProcesados();
    }

    private void anularTotal(String idCompra, String idCliente, String montoRevertido) {
        ingesta.recibirAnulacion(anulacion(idCompra + "-A", idCompra, idCliente, "TOTAL", montoRevertido, ""));
        exigirProcesados();
    }

    private void devolverParcial(String idCompra, String idCliente, String categoria, String monto) {
        String items = ", \"items\": [ { \"categoria\": \"" + categoria + "\", \"cantidad\": 1, \"monto\": "
                + monto + " } ]";
        ingesta.recibirAnulacion(anulacion(idCompra + "-D" + UUID.randomUUID(), idCompra, idCliente, "PARCIAL",
                monto, items));
        exigirProcesados();
    }

    private void exigirProcesados() {
        assertThat(eventos.findAll()).allMatch(e -> e.getEstado() == EstadoEvento.PROCESADO);
    }

    private String nivelPersistido(String idClienteOrigen) {
        return jdbc.query("SELECT nivel FROM comportamiento.cliente_nivel_consumo WHERE id_cliente_origen = ?",
                rs -> rs.next() ? rs.getString(1) : null, idClienteOrigen);
    }

    private BigDecimal valorPersistido(String idClienteOrigen) {
        return jdbc.queryForObject("SELECT valor_acumulado FROM comportamiento.cliente_nivel_consumo "
                + "WHERE id_cliente_origen = ?", BigDecimal.class, idClienteOrigen);
    }

    private static String compra(String idCompra, String idCliente, String montoTotal, String items) {
        return """
                {
                  "idEvento": "%s",
                  "tipoEvento": "COMPRA_CONFIRMADA",
                  "fechaEmision": "2026-09-20T10:05:00Z",
                  "compra": {
                    "idCompra": "%s",
                    "idCliente": "%s",
                    "fecha": "%s",
                    "montoTotal": %s,
                    "items": [ %s ]
                  }
                }
                """.formatted(UUID.randomUUID(), idCompra, idCliente, FECHA_COMPRA, montoTotal, items);
    }

    private static String anulacion(String idAnulacion, String idCompra, String idCliente, String tipo,
                                    String montoRevertido, String items) {
        return """
                {
                  "idEvento": "%s",
                  "tipoEvento": "COMPRA_ANULADA",
                  "fechaEmision": "2026-09-25T10:05:00Z",
                  "anulacion": {
                    "idAnulacion": "%s",
                    "idCompra": "%s",
                    "idCliente": "%s",
                    "tipo": "%s",
                    "fecha": "%s",
                    "montoRevertido": %s,
                    "motivo": "Prueba de nivel de consumo"%s
                  }
                }
                """.formatted(UUID.randomUUID(), idAnulacion, idCompra, idCliente, tipo, FECHA_ANULACION,
                montoRevertido, items);
    }
}
