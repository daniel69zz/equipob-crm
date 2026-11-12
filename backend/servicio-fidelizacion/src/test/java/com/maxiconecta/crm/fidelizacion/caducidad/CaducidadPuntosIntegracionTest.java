package com.maxiconecta.crm.fidelizacion.caducidad;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxiconecta.crm.fidelizacion.movimiento.BitacoraMovimientos;
import com.maxiconecta.crm.fidelizacion.movimiento.BitacoraMovimientos.Estado;
import com.maxiconecta.crm.fidelizacion.movimiento.BitacoraMovimientos.EventoMovimiento;
import com.maxiconecta.crm.fidelizacion.movimiento.ClientesPerfil;
import com.maxiconecta.crm.fidelizacion.movimiento.IngestaMovimientos;
import com.maxiconecta.crm.fidelizacion.parametro.CambioParametro;
import com.maxiconecta.crm.fidelizacion.parametro.CodigosParametros;
import com.maxiconecta.crm.fidelizacion.parametro.Parametro;
import com.maxiconecta.crm.fidelizacion.parametro.ParametrosGenerales;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-420 · Caducidad de puntos (SCRUM-50, docs/fidelizacion/caducidad-puntos.md) con PostgreSQL real, los
 * movimientos RIO-CRM-03 y RIO-CRM-05 de Marketplace y Ventas, el servicio de Perfil simulado y la identidad que
 * reenvía el Gateway. Las fechas son relativas a hoy: una compra de hace 13 meses ya venció con la vigencia de 12
 * meses. CLI-5521 es el cliente 42 del CRM; una compra de Bs 350.50 le da 35 puntos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CaducidadPuntosIntegracionTest {

    private static final long CLIENTE = 42;
    private static final String ADMINISTRADOR_PERMISOS = "FIDELIZACION_CONFIGURAR,PUNTOS_CONSULTAR";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registro) {
        // La consulta del Agente registra en la auditoría compartida, que crean las migraciones del Gateway.
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("seguridad").locations("filesystem:../api-gateway/src/main/resources/db/migration").load()
                .migrate();
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockBean
    private ClientesPerfil perfiles;

    @Autowired
    private IngestaMovimientos ingesta;

    @Autowired
    private BitacoraMovimientos bitacora;

    @Autowired
    private ProcesoCaducidad proceso;

    @Autowired
    private CaducidadProgramada programada;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private ParametrosGenerales parametros;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE fidelizacion.movimiento_puntos, fidelizacion.evento_movimiento, fidelizacion.saldo_puntos, "
                + "fidelizacion.cambio_nivel");
        when(perfiles.buscar(anyString())).thenReturn(Optional.of(CLIENTE));
        when(perfiles.buscar("CLI-7")).thenReturn(Optional.of(7L));
        when(perfiles.buscar("CLI-8")).thenReturn(Optional.of(8L));
        cambiarVigencia(12);
    }

    /** Deja la vigencia de los puntos en {@code meses}, como lo haría el Administrador de CRM (SCRUM-517). */
    private void cambiarVigencia(int meses) {
        Parametro actual = parametros.listar().stream()
                .filter(p -> p.codigo().equals(CodigosParametros.PUNTOS_VIGENCIA_MESES)).findFirst().orElseThrow();
        if (actual.valor().intValue() != meses) {
            parametros.actualizar(List.of(new CambioParametro(CodigosParametros.PUNTOS_VIGENCIA_MESES,
                    BigDecimal.valueOf(meses), actual.version())), "admin");
        }
    }

    // --- SCRUM-517: la vigencia es un parámetro general y rige en la consulta y la caducidad siguientes ---

    @Test
    void alAmpliarLaVigenciaElSaldoVuelveAEstarDisponibleYLaCaducidadNoLoToca() throws Exception {
        OffsetDateTime compra = haceMeses(13);
        acumular("V-VIG-1", "CLI-5521", "350.50", compra);
        assertThat(consultarSaldo().get("puntosCaducados").asLong()).isEqualTo(35);

        cambiarVigencia(18);

        JsonNode saldo = consultarSaldo();
        assertThat(saldo.get("saldo").asLong()).isEqualTo(35);
        assertThat(saldo.get("puntosCaducados").asLong()).isZero();
        assertThat(OffsetDateTime.parse(saldo.get("fechaCaducidad").asText()).toInstant())
                .isEqualTo(compra.plusMonths(18).toInstant());
        proceso.caducarVencidos();
        assertThat(saldo(CLIENTE)).isEqualTo(35);
        assertThat(caducidades()).isZero();
        mvc.perform(como("admin", "ADMINISTRADOR_CRM", ADMINISTRADOR_PERMISOS,
                        get("/api/fidelizacion/caducidad/configuracion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vigenciaMeses").value(18));
    }

    @Test
    void alReducirLaVigenciaLaCaducidadSiguienteVenceLosPuntosConElValorNuevo() {
        acumular("V-VIG-2", "CLI-5521", "350.50", haceMeses(7));
        proceso.caducarVencidos();
        assertThat(saldo(CLIENTE)).isEqualTo(35);

        cambiarVigencia(6);
        proceso.caducarVencidos();

        assertThat(saldo(CLIENTE)).isZero();
        assertThat(caducidades()).isEqualTo(1);
    }

    // --- Criterio 1: los puntos vencidos se descuentan y queda un movimiento de caducidad ---

    @Test
    void losPuntosVencidosSeDescuentanDelSaldoYQuedaUnMovimientoDeCaducidad() throws Exception {
        OffsetDateTime compra = haceMeses(13);
        acumular("V-1", "CLI-5521", "350.50", compra);

        mvc.perform(como("admin", "ADMINISTRADOR_CRM", ADMINISTRADOR_PERMISOS, post("/api/fidelizacion/caducidad/ejecutar")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vigenciaMeses").value(12))
                .andExpect(jsonPath("$.clientesConPuntosCaducados").value(1))
                .andExpect(jsonPath("$.puntosCaducados").value(35))
                .andExpect(jsonPath("$.clientesConError").value(0));

        assertThat(saldo(CLIENTE)).isZero();
        Map<String, Object> caducidad = jdbc.queryForMap("SELECT * FROM fidelizacion.movimiento_puntos "
                + "WHERE tipo_movimiento = 'CADUCIDAD'");
        OffsetDateTime vencimiento = compra.withOffsetSameInstant(ZoneOffset.UTC).plusMonths(12);
        assertThat(caducidad.get("id_transaccion")).isEqualTo(ReglaCaducidad.idMovimiento(CLIENTE, vencimiento));
        assertThat(caducidad.get("cliente_id")).isEqualTo(CLIENTE);
        assertThat(caducidad.get("id_cliente_origen")).isEqualTo("CLI-5521");
        assertThat(caducidad.get("puntos")).isEqualTo(35L);
        assertThat(caducidad.get("saldo_resultante")).isEqualTo(0L);
        assertThat(caducidad.get("id_evento_movimiento")).isNull();
        assertThat(jdbc.queryForObject("SELECT fecha_transaccion FROM fidelizacion.movimiento_puntos "
                + "WHERE tipo_movimiento = 'CADUCIDAD'", OffsetDateTime.class).toInstant()).isEqualTo(vencimiento.toInstant());

        mvc.perform(get("/api/fidelizacion/clientes/42/movimientos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipoMovimiento").value("CADUCIDAD"))
                .andExpect(jsonPath("$[0].puntos").value(35))
                .andExpect(jsonPath("$[0].variacionSaldo").value(-35))
                .andExpect(jsonPath("$[0].saldoResultante").value(0));
    }

    @Test
    void elProcesoAutomaticoCaducaSoloALosClientesConLaVigenciaVencida() {
        acumular("V-1", "CLI-5521", "350.50", haceMeses(13));
        acumular("V-2", "CLI-7", "100.00", haceMeses(14));
        acumular("V-3", "CLI-8", "200.00", haceDias(5));

        programada.caducar();

        assertThat(saldo(CLIENTE)).isZero();
        assertThat(saldo(7)).isZero();
        assertThat(saldo(8)).isEqualTo(20);
        assertThat(caducidades()).isEqualTo(2);
    }

    // --- Criterio 2: los puntos vigentes no se modifican ---

    @Test
    void losPuntosAunVigentesNoSeModifican() {
        acumular("V-1", "CLI-5521", "350.50", haceDias(10));

        ProcesoCaducidad.Ejecucion ejecucion = proceso.caducarVencidos();

        assertThat(ejecucion.clientesConPuntosCaducados()).isZero();
        assertThat(saldo(CLIENTE)).isEqualTo(35);
        assertThat(caducidades()).isZero();
    }

    @Test
    void unaCompraDentroDeLaVigenciaRenuevaLaVigenciaDeTodoElSaldo() {
        acumular("V-1", "CLI-5521", "350.50", haceMeses(13));
        acumular("V-2", "CLI-5521", "350.50", haceMeses(11));

        proceso.caducarVencidos();

        assertThat(saldo(CLIENTE)).isEqualTo(70);
        assertThat(caducidades()).isZero();
    }

    @Test
    void unClienteQueYaRedimioTodoSuSaldoNoSeToca() {
        acumular("V-1", "CLI-5521", "350.50", haceMeses(13));
        assertThat(ingesta.recibir(redencion("R-1", 35, haceMeses(12)))).isEqualTo(Estado.PROCESADO);

        assertThat(proceso.caducarVencidos().clientesConPuntosCaducados()).isZero();
        assertThat(caducidades()).isZero();
    }

    // --- Criterio 3: ejecutado dos veces el mismo día no caduca los mismos puntos dos veces ---

    @Test
    void ejecutarDosVecesElMismoDiaNoCaducaDosVecesLosMismosPuntos() {
        acumular("V-1", "CLI-5521", "350.50", haceMeses(13));

        ProcesoCaducidad.Ejecucion primera = proceso.caducarVencidos();
        ProcesoCaducidad.Ejecucion segunda = proceso.caducarVencidos();

        assertThat(primera.puntosCaducados()).isEqualTo(35);
        assertThat(segunda.clientesConPuntosCaducados()).isZero();
        assertThat(segunda.puntosCaducados()).isZero();
        assertThat(caducidades()).isEqualTo(1);
        assertThat(saldo(CLIENTE)).isZero();
    }

    @Test
    void dosEjecucionesSimultaneasCaducanUnaSolaVez() throws Exception {
        acumular("V-1", "CLI-5521", "350.50", haceMeses(13));
        acumular("V-2", "CLI-7", "100.00", haceMeses(13));

        List<ProcesoCaducidad.Ejecucion> ejecuciones = enParalelo(2);

        assertThat(ejecuciones.stream().mapToLong(ProcesoCaducidad.Ejecucion::puntosCaducados).sum()).isEqualTo(35 + 10);
        assertThat(ejecuciones.stream().mapToInt(ProcesoCaducidad.Ejecucion::clientesConError).sum()).isZero();
        assertThat(caducidades()).isEqualTo(2);
    }

    @Test
    void cadaVencimientoTieneSuPropioMovimiento() {
        acumular("V-1", "CLI-5521", "350.50", haceMeses(26));
        // La segunda compra llega con los 35 puntos de la primera ya vencidos: caducan antes de acumular.
        acumular("V-2", "CLI-5521", "100.00", haceMeses(13));
        assertThat(saldo(CLIENTE)).isEqualTo(10);

        proceso.caducarVencidos();

        assertThat(saldo(CLIENTE)).isZero();
        assertThat(jdbc.queryForList("SELECT puntos FROM fidelizacion.movimiento_puntos "
                + "WHERE tipo_movimiento = 'CADUCIDAD' ORDER BY fecha_transaccion", Long.class)).containsExactly(35L, 10L);
    }

    // --- SCRUM-419: la caducidad en las consultas y en los movimientos ---

    @Test
    void lasConsultasMuestranElSaldoVencidoComoCaducadoAunqueElProcesoNoHayaCorrido() throws Exception {
        OffsetDateTime compra = haceMeses(13);
        acumular("V-1", "CLI-5521", "350.50", compra);

        JsonNode saldo = consultarSaldo();
        assertThat(saldo.get("saldo").asLong()).isZero();
        assertThat(saldo.get("puntosCaducados").asLong()).isEqualTo(35);
        assertThat(OffsetDateTime.parse(saldo.get("fechaCaducidad").asText()).toInstant())
                .isEqualTo(compra.plusMonths(12).toInstant());
        mvc.perform(get("/api/fidelizacion/clientes/42/puntos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(0))
                .andExpect(jsonPath("$.puntosCaducados").value(35));
        assertThat(saldo(CLIENTE)).as("las consultas no descuentan nada").isEqualTo(35);

        proceso.caducarVencidos();

        JsonNode despues = consultarSaldo();
        assertThat(despues.get("saldo").asLong()).isZero();
        assertThat(despues.get("puntosCaducados").asLong()).isZero();
        assertThat(despues.get("ultimosMovimientos").get(0).get("tipoMovimiento").asText()).isEqualTo("CADUCIDAD");
        assertThat(despues.get("ultimosMovimientos").get(0).get("variacionSaldo").asLong()).isEqualTo(-35);
    }

    @Test
    void unaCompraDespuesDelVencimientoNoReviveLosPuntosVencidos() {
        acumular("V-1", "CLI-5521", "350.50", haceMeses(13));

        acumular("V-2", "CLI-5521", "350.50", haceDias(1));

        assertThat(saldo(CLIENTE)).isEqualTo(35);
        assertThat(caducidades()).isEqualTo(1);
    }

    @Test
    void unaRedencionDespuesDelVencimientoFallaPorqueLosPuntosCaducaron() {
        acumular("V-1", "CLI-5521", "350.50", haceMeses(13));

        assertThat(ingesta.recibir(redencion("R-1", 20, haceDias(1)))).isEqualTo(Estado.FALLIDO);

        assertThat(causaDe("R-1")).contains("Saldo insuficiente para la redención R-1")
                .contains("el saldo disponible es 0").contains("35 puntos caducaron el");
        // La redención falla entera: tampoco queda la caducidad que se aplicó en su transacción.
        assertThat(saldo(CLIENTE)).isEqualTo(35);
        assertThat(caducidades()).isZero();
    }

    @Test
    void unaRedencionHechaAntesDelVencimientoSeAplicaAunqueLlegueDespues() {
        OffsetDateTime compra = haceMeses(13);
        acumular("V-1", "CLI-5521", "350.50", compra);

        assertThat(ingesta.recibir(redencion("R-1", 20, compra.plusMonths(11)))).isEqualTo(Estado.PROCESADO);
        assertThat(saldo(CLIENTE)).isEqualTo(15);

        assertThat(proceso.caducarVencidos().puntosCaducados()).isEqualTo(15);
    }

    @Test
    void laReversaDeUnaCompraCuyosPuntosCaducaronNoDescuentaPuntosDeComprasPosteriores() {
        acumular("V-1", "CLI-5521", "350.50", haceMeses(13));
        proceso.caducarVencidos();
        acumular("V-2", "CLI-5521", "350.50", haceDias(2));

        assertThat(ingesta.recibir(anulacion("V-1-A1", "V-1", "350.50", haceDias(1)))).isEqualTo(Estado.PROCESADO);

        assertThat(saldo(CLIENTE)).isEqualTo(35);
        Map<String, Object> reversa = jdbc.queryForMap("SELECT puntos, puntos_no_recuperados FROM "
                + "fidelizacion.movimiento_puntos WHERE id_transaccion = 'V-1-A1' AND tipo_movimiento = 'REVERSA'");
        assertThat(reversa.get("puntos")).isEqualTo(35L);
        assertThat(reversa.get("puntos_no_recuperados")).isEqualTo(35L);
    }

    @Test
    void unEventoDeVentasNoPuedeTraerUnaCaducidad() {
        String evento = """
                {"idEvento": "%s", "tipoEvento": "MOVIMIENTO_PUNTOS", "fechaEmision": "%s",
                 "movimiento": {"idTransaccion": "CADUCIDAD-42-X", "idCliente": "CLI-5521",
                                "tipoMovimiento": "CADUCIDAD", "puntos": 10, "fecha": "%s"}}
                """.formatted(UUID.randomUUID(), haceDias(1), haceDias(1));

        assertThat(ingesta.recibir(evento)).isEqualTo(Estado.FALLIDO);
        assertThat(causaDe("CADUCIDAD-42-X")).contains("movimiento.tipoMovimiento debe ser ACUMULACION, REDENCION o REVERSA");
    }

    // --- Ejecución manual y configuración ---

    @Test
    void soloElAdministradorDeCrmEjecutaLaCaducidad() throws Exception {
        acumular("V-1", "CLI-5521", "350.50", haceMeses(13));

        mvc.perform(como("agente", "AGENTE_ATENCION", "PUNTOS_CONSULTAR,FIDELIZACION_CONFIGURAR",
                        post("/api/fidelizacion/caducidad/ejecutar")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/fidelizacion/caducidad/ejecutar")).andExpect(status().isUnauthorized());

        assertThat(saldo(CLIENTE)).isEqualTo(35);
        assertThat(caducidades()).isZero();
    }

    @Test
    void laConfiguracionMuestraLaVigenciaYElHorarioDelProceso() throws Exception {
        mvc.perform(como("agente", "AGENTE_ATENCION", "PUNTOS_CONSULTAR", get("/api/fidelizacion/caducidad/configuracion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vigenciaMeses").value(12))
                .andExpect(jsonPath("$.regla").value("El saldo completo caduca 12 meses después de la última compra que acumuló puntos"))
                .andExpect(jsonPath("$.procesoCron").value("0 0 2 * * *"))
                .andExpect(jsonPath("$.zonaHoraria").value("America/La_Paz"));
        mvc.perform(get("/api/fidelizacion/caducidad/configuracion")).andExpect(status().isUnauthorized());
    }

    // --- Ayudas ---

    private void acumular(String idTransaccion, String idCliente, String valor, OffsetDateTime fecha) {
        assertThat(ingesta.recibir("""
                {"idEvento": "%s", "tipoEvento": "MOVIMIENTO_PUNTOS", "fechaEmision": "%s",
                 "movimiento": {"idTransaccion": "%s", "idCliente": "%s", "tipoMovimiento": "ACUMULACION",
                                "valorConsumido": %s, "fecha": "%s"}}
                """.formatted(UUID.randomUUID(), fecha, idTransaccion, idCliente, valor, fecha)))
                .isEqualTo(Estado.PROCESADO);
    }

    private static String redencion(String idTransaccion, long puntos, OffsetDateTime fecha) {
        return """
                {"idEvento": "%s", "tipoEvento": "MOVIMIENTO_PUNTOS", "fechaEmision": "%s",
                 "movimiento": {"idTransaccion": "%s", "idCliente": "CLI-5521", "tipoMovimiento": "REDENCION",
                                "puntos": %d, "fecha": "%s"}}
                """.formatted(UUID.randomUUID(), fecha, idTransaccion, puntos, fecha);
    }

    private static String anulacion(String idAnulacion, String idCompra, String monto, OffsetDateTime fecha) {
        return """
                {"idEvento": "%s", "tipoEvento": "COMPRA_ANULADA", "fechaEmision": "%s",
                 "anulacion": {"idAnulacion": "%s", "idCompra": "%s", "idCliente": "CLI-5521", "tipo": "TOTAL",
                               "fecha": "%s", "montoRevertido": %s, "motivo": "Prueba de caducidad"}}
                """.formatted(UUID.randomUUID(), fecha, idAnulacion, idCompra, fecha, monto);
    }

    private JsonNode consultarSaldo() throws Exception {
        String cuerpo = mvc.perform(como("agente", "AGENTE_ATENCION", "PUNTOS_CONSULTAR",
                        get("/api/fidelizacion/clientes/{id}/saldo", CLIENTE)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private List<ProcesoCaducidad.Ejecucion> enParalelo(int ejecuciones) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(ejecuciones);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<ProcesoCaducidad.Ejecucion>> tareas = java.util.stream.IntStream.range(0, ejecuciones)
                    .mapToObj(i -> hilos.submit(() -> {
                        salida.await();
                        return proceso.caducarVencidos();
                    })).toList();
            salida.countDown();
            List<ProcesoCaducidad.Ejecucion> resultados = new java.util.ArrayList<>();
            for (Future<ProcesoCaducidad.Ejecucion> tarea : tareas) {
                resultados.add(tarea.get());
            }
            return resultados;
        } finally {
            hilos.shutdownNow();
        }
    }

    private String causaDe(String idTransaccion) {
        return bitacora.listar(null, 100).stream().filter(e -> idTransaccion.equals(e.idTransaccion()))
                .findFirst().map(EventoMovimiento::causa).orElse(null);
    }

    private long saldo(long clienteId) {
        return jdbc.queryForObject("SELECT saldo FROM fidelizacion.saldo_puntos WHERE cliente_id = ?", Long.class, clienteId);
    }

    private long caducidades() {
        return jdbc.queryForObject("SELECT count(*) FROM fidelizacion.movimiento_puntos WHERE tipo_movimiento = 'CADUCIDAD'",
                Long.class);
    }

    private static OffsetDateTime haceMeses(int meses) {
        return OffsetDateTime.now(ZoneOffset.UTC).minusMonths(meses).truncatedTo(ChronoUnit.SECONDS);
    }

    private static OffsetDateTime haceDias(int dias) {
        return OffsetDateTime.now(ZoneOffset.UTC).minusDays(dias).truncatedTo(ChronoUnit.SECONDS);
    }

    private static MockHttpServletRequestBuilder como(String usuario, String rol, String permisos,
                                                      MockHttpServletRequestBuilder solicitud) {
        return solicitud.header("X-Usuario", usuario).header("X-Usuario-Rol", rol).header("X-Usuario-Permisos", permisos);
    }
}
