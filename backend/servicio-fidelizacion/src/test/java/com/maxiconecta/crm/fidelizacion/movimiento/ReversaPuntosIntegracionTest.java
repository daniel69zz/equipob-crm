package com.maxiconecta.crm.fidelizacion.movimiento;

import com.maxiconecta.crm.fidelizacion.configuracion.ConfiguracionRabbit;
import com.maxiconecta.crm.fidelizacion.movimiento.BitacoraMovimientos.Estado;
import com.maxiconecta.crm.fidelizacion.movimiento.BitacoraMovimientos.EventoMovimiento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.maxiconecta.crm.fidelizacion.movimiento.LectorMovimientosTest.ejemplo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-524 / SCRUM-542 · Reversa de puntos por compra anulada o devuelta (docs/fidelizacion/reversa-puntos.md)
 * con eventos simulados RIO-CRM-03 y RIO-CRM-05 publicados en RabbitMQ, PostgreSQL real y el servicio de
 * Perfil simulado. CLI-5521 es el cliente 42 del CRM; su compra V-100234 de Bs 350.50 le da 35 puntos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ReversaPuntosIntegracionTest {

    private static final long CLIENTE = 42;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @MockBean
    private ClientesPerfil perfiles;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private IngestaMovimientos ingesta;

    @Autowired
    private BitacoraMovimientos bitacora;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE fidelizacion.movimiento_puntos, fidelizacion.evento_movimiento, fidelizacion.saldo_puntos, "
                + "fidelizacion.cambio_nivel");
        jdbc.update("DELETE FROM fidelizacion.umbral_nivel WHERE version > 1");
        jdbc.update("DELETE FROM fidelizacion.configuracion_niveles WHERE version > 1");
        jdbc.update("DELETE FROM fidelizacion.regla_acumulacion WHERE version > 1");
        when(perfiles.buscar(anyString())).thenReturn(Optional.of(CLIENTE));
    }

    // --- Criterio 1: una anulación total descuenta todos los puntos, con referencia al movimiento original ---

    @Test
    void unaAnulacionTotalRioCrm05PublicadaEnRabbitRevierteTodosLosPuntos() {
        acumularLos35DeAna();
        publicarAnulacion(anulacion("V-100234-A9", "TOTAL", "350.50"));

        await().atMost(Duration.ofSeconds(15)).until(() -> estadoDe("V-100234-A9") == Estado.PROCESADO);
        assertThat(saldo()).isZero();
    }

    @Test
    void unaReversaRioCrm03PublicadaEnRabbitTambienRevierteLosPuntos() {
        acumularLos35DeAna();
        publicarMovimiento(ejemplo("movimiento-reversa-parcial.json"));

        await().atMost(Duration.ofSeconds(15)).until(() -> estadoDe("V-100234-D1") == Estado.PROCESADO);
        assertThat(saldo()).isEqualTo(30);
    }

    @Test
    void laReversaQuedaEnElHistorialConReferenciaALaCompraQueRevierte() throws Exception {
        acumularLos35DeAna();
        ingesta.recibir(anulacion("V-100234-A9", "TOTAL", "350.50"));

        mvc.perform(get("/api/fidelizacion/clientes/42/movimientos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].idTransaccion").value("V-100234-A9"))
                .andExpect(jsonPath("$[0].tipoMovimiento").value("REVERSA"))
                .andExpect(jsonPath("$[0].idTransaccionRevertida").value("V-100234"))
                .andExpect(jsonPath("$[0].valorConsumido").value(350.50))
                .andExpect(jsonPath("$[0].puntos").value(35))
                .andExpect(jsonPath("$[0].variacionSaldo").value(-35))
                .andExpect(jsonPath("$[0].puntosNoRecuperados").value(0))
                .andExpect(jsonPath("$[0].versionRegla").value(1))
                .andExpect(jsonPath("$[0].saldoResultante").value(0))
                .andExpect(jsonPath("$[1].idTransaccion").value("V-100234"))
                .andExpect(jsonPath("$[1].idTransaccionRevertida").value(nullValue()));
    }

    // --- Criterio 2: una devolución parcial descuenta solo los puntos del monto devuelto ---

    @Test
    void unaDevolucionParcialRevierteSoloLosPuntosDelMontoDevuelto() {
        acumularLos35DeAna();

        assertThat(ingesta.recibir(ejemplo("anulacion-parcial.json"))).isEqualTo(Estado.PROCESADO);

        assertThat(saldo()).isEqualTo(30);
        assertThat(puntosDe("V-100234-D1")).isEqualTo(5);
    }

    @Test
    void laDevolucionYLaAnulacionDelRestoDejanLaCompraSinPuntos() {
        acumularLos35DeAna();
        ingesta.recibir(ejemplo("anulacion-parcial.json"));
        ingesta.recibir(ejemplo("anulacion-total.json"));

        assertThat(saldo()).isZero();
        assertThat(puntosDe("V-100234-D1") + puntosDe("V-100234-A1")).isEqualTo(35);
    }

    @Test
    void laReversaUsaLaReglaConLaQueSeAcumuloLaCompraYNoLaVigente() {
        acumularLos35DeAna();
        // La regla cambia a 1 punto por cada Bs 5: la devolución sigue usando 1 punto por cada Bs 10.
        jdbc.update("INSERT INTO fidelizacion.regla_acumulacion (version, bolivianos_por_punto, monto_minimo, modificado_por) "
                + "VALUES (2, 5.00, 0.00, 'prueba')");

        ingesta.recibir(ejemplo("anulacion-parcial.json"));

        assertThat(puntosDe("V-100234-D1")).isEqualTo(5);
        assertThat(saldo()).isEqualTo(30);
    }

    // --- Criterio 3: una reversa ya aplicada no se descuenta otra vez ---

    @Test
    void unaReversaReentregadaNoSeDescuentaDosVeces() {
        acumularLos35DeAna();
        ingesta.recibir(ejemplo("anulacion-parcial.json"));

        assertThat(ingesta.recibir(ejemplo("anulacion-parcial.json"))).isEqualTo(Estado.DESCARTADO);

        assertThat(saldo()).isEqualTo(30);
        assertThat(bitacora.listar(Estado.DESCARTADO, 10).get(0).causa())
                .isEqualTo("La transacción V-100234-D1 ya revirtió sus puntos (REVERSA)");
    }

    @Test
    void laMismaReversaPorRioCrm03YPorRioCrm05SeAplicaUnaSolaVez() {
        acumularLos35DeAna();

        assertThat(ingesta.recibir(ejemplo("movimiento-reversa-parcial.json"))).isEqualTo(Estado.PROCESADO);
        assertThat(ingesta.recibir(ejemplo("anulacion-parcial.json"))).isEqualTo(Estado.DESCARTADO);

        assertThat(saldo()).isEqualTo(30);
    }

    @Test
    void dosCopiasSimultaneasDeLaMismaReversaDescuentanUnaSolaVez() throws Exception {
        acumularLos35DeAna();
        String anulacion = anulacion("V-100234-A9", "TOTAL", "350.50");

        List<Estado> resultados = enParalelo(anulacion, anulacion, anulacion);

        assertThat(resultados).filteredOn(e -> e == Estado.PROCESADO).hasSize(1);
        assertThat(resultados).filteredOn(e -> e == Estado.DESCARTADO).hasSize(2);
        assertThat(saldo()).isZero();
    }

    @Test
    void dosDevolucionesSimultaneasNoRevierteMasDeLoQueSigueVigente() throws Exception {
        acumularLos35DeAna();

        List<Estado> resultados = enParalelo(anulacion("V-100234-D7", "PARCIAL", "200.00"),
                anulacion("V-100234-D8", "PARCIAL", "200.00"));

        assertThat(resultados).containsExactlyInAnyOrder(Estado.PROCESADO, Estado.FALLIDO);
        assertThat(saldo()).isEqualTo(15);   // vigente 150.50 → 15 puntos
    }

    @Test
    void unaCompraYaAnuladaPorCompletoNoSeRevierteOtraVez() {
        acumularLos35DeAna();
        ingesta.recibir(anulacion("V-100234-A9", "TOTAL", "350.50"));

        assertThat(ingesta.recibir(ejemplo("anulacion-parcial.json"))).isEqualTo(Estado.FALLIDO);

        assertThat(causaDe("V-100234-D1"))
                .contains("La reversa V-100234-D1 (50.50) supera lo que sigue vigente de la transacción V-100234 (0.00)");
        assertThat(saldo()).isZero();
    }

    // --- Saldo insuficiente: el cliente ya redimió parte de los puntos (SCRUM-542) ---

    @Test
    void siElClienteYaRedimioLosPuntosElSaldoQuedaEnCeroYLaDiferenciaQuedaRegistrada() throws Exception {
        acumularLos35DeAna();
        ingesta.recibir(ejemplo("movimiento-redencion.json"));   // redime 20: saldo 15

        assertThat(ingesta.recibir(anulacion("V-100234-A9", "TOTAL", "350.50"))).isEqualTo(Estado.PROCESADO);

        assertThat(saldo()).isZero();
        mvc.perform(get("/api/fidelizacion/clientes/42/movimientos"))
                .andExpect(jsonPath("$[0].idTransaccion").value("V-100234-A9"))
                .andExpect(jsonPath("$[0].puntos").value(35))
                .andExpect(jsonPath("$[0].puntosNoRecuperados").value(20))
                .andExpect(jsonPath("$[0].variacionSaldo").value(-15));
    }

    // --- Criterio 4: tras la reversa el cliente queda en el nivel que corresponde ---

    @Test
    void laReversaBajaElNivelYRegistraElCambioConLaReversaComoOrigen() throws Exception {
        plataDesde30Puntos();
        acumularLos35DeAna();
        assertThat(nivel()).isEqualTo("PLATA");

        ingesta.recibir(anulacion("V-100234-A9", "TOTAL", "350.50"));

        assertThat(nivel()).isEqualTo("BRONCE");
        mvc.perform(get("/api/fidelizacion/clientes/42/cambios-nivel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nivelAnterior").value("PLATA"))
                .andExpect(jsonPath("$[0].nivelNuevo").value("BRONCE"))
                .andExpect(jsonPath("$[0].puntosPeriodo").value(0))
                .andExpect(jsonPath("$[0].origen").value("MOVIMIENTO"))
                .andExpect(jsonPath("$[0].idTransaccion").value("V-100234-A9"))
                .andExpect(jsonPath("$[0].tipoMovimiento").value("REVERSA"))
                .andExpect(jsonPath("$[1].nivelNuevo").value("PLATA"))
                .andExpect(jsonPath("$[1].tipoMovimiento").value("ACUMULACION"));
    }

    @Test
    void unaDevolucionQueNoCruzaElUmbralNoCambiaElNivel() {
        plataDesde30Puntos();
        acumularLos35DeAna();

        ingesta.recibir(ejemplo("anulacion-parcial.json"));   // 35 → 30: sigue en PLATA

        assertThat(nivel()).isEqualTo("PLATA");
        assertThat(cambiosDeNivel()).isEqualTo(1);   // solo el de la acumulación
    }

    @Test
    void laReversaDeUnaCompraFueraDelPeriodoNoLeQuitaNivelPorComprasRecientes() {
        plataDesde30Puntos();
        ingesta.recibir(acumulacion("V-ANTIGUA", "1000.00", OffsetDateTime.now().minusMonths(13)));
        acumularLos35DeAna();
        assertThat(nivel()).isEqualTo("PLATA");   // solo cuentan los 35 de la compra reciente

        ingesta.recibir(anulacion("V-ANTIGUA-A1", "V-ANTIGUA", "TOTAL", "1000.00"));

        assertThat(saldo()).isEqualTo(35);
        assertThat(nivel()).isEqualTo("PLATA");
    }

    @Test
    void unaReversaFallidaNoCambiaElNivel() {
        plataDesde30Puntos();
        acumularLos35DeAna();

        ingesta.recibir(anulacion("V-100234-A9", "TOTAL", "999.00"));

        assertThat(nivel()).isEqualTo("PLATA");
        assertThat(saldo()).isEqualTo(35);
    }

    // --- Reproceso y errores ---

    @Test
    void unaReversaQueLlegaAntesQueLaAcumulacionSeReprocesaDespues() throws Exception {
        assertThat(ingesta.recibir(ejemplo("anulacion-parcial.json"))).isEqualTo(Estado.FALLIDO);
        assertThat(causaDe("V-100234-D1")).contains("La transacción V-100234 no tiene una acumulación de puntos registrada");
        long evento = bitacora.listar(Estado.FALLIDO, TipoMovimiento.REVERSA, 10).get(0).id();

        acumularLos35DeAna();
        mvc.perform(post("/api/fidelizacion/eventos/" + evento + "/reprocesar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PROCESADO"));

        assertThat(saldo()).isEqualTo(30);
    }

    @Test
    void unaReversaDeOtroClienteQuedaFallida() {
        acumularLos35DeAna();
        when(perfiles.buscar("CLI-9999")).thenReturn(Optional.of(77L));

        ingesta.recibir(ejemplo("anulacion-parcial.json").replace("CLI-5521", "CLI-9999"));

        assertThat(causaDe("V-100234-D1")).contains("El cliente CLI-9999 no es el de la transacción V-100234");
        assertThat(saldo()).isEqualTo(35);
    }

    @Test
    void lasReversasFallidasSeFiltranEnLaBitacora() throws Exception {
        ingesta.recibir(ejemplo("anulacion-parcial.json"));
        ingesta.recibir(ejemplo("movimiento-redencion.json"));

        mvc.perform(get("/api/fidelizacion/eventos").param("estado", "FALLIDO").param("tipo", "REVERSA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idTransaccion").value("V-100234-D1"));
    }

    // --- Ayudas ---

    private void acumularLos35DeAna() {
        assertThat(ingesta.recibir(ejemplo("movimiento-acumulacion-ana.json"))).isEqualTo(Estado.PROCESADO);
    }

    /** Versión 2 de los niveles, sin recalcular a nadie: Bronce 0–29, Plata 30–1.999, Oro desde 2.000. */
    private void plataDesde30Puntos() {
        jdbc.update("INSERT INTO fidelizacion.configuracion_niveles (version, periodo_meses, modificado_por) "
                + "VALUES (2, 12, 'prueba')");
        jdbc.update("INSERT INTO fidelizacion.umbral_nivel (version, nivel, puntos_minimos, puntos_maximos) VALUES "
                + "(2, 'BRONCE', 0, 29), (2, 'PLATA', 30, 1999), (2, 'ORO', 2000, NULL)");
    }

    private void publicarMovimiento(String contenido) {
        publicar(ConfiguracionRabbit.RUTA_MOVIMIENTO_PUNTOS, contenido);
    }

    private void publicarAnulacion(String contenido) {
        publicar(ConfiguracionRabbit.RUTA_ANULACION, contenido);
    }

    private void publicar(String ruta, String contenido) {
        rabbit.send(ConfiguracionRabbit.EXCHANGE_VENTAS, ruta, new Message(contenido.getBytes(StandardCharsets.UTF_8)));
    }

    private Estado estadoDe(String idTransaccion) {
        return ultimoEvento(idTransaccion).map(EventoMovimiento::estado).orElse(null);
    }

    private String causaDe(String idTransaccion) {
        return ultimoEvento(idTransaccion).map(EventoMovimiento::causa).orElse(null);
    }

    private Optional<EventoMovimiento> ultimoEvento(String idTransaccion) {
        return bitacora.listar(null, 100).stream().filter(e -> idTransaccion.equals(e.idTransaccion())).findFirst();
    }

    private List<Estado> enParalelo(String... contenidos) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(contenidos.length);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<Estado>> tareas = Arrays.stream(contenidos)
                    .map(c -> hilos.submit(() -> {
                        salida.await();
                        return ingesta.recibir(c);
                    })).toList();
            salida.countDown();
            List<Estado> resultados = new ArrayList<>();
            for (Future<Estado> tarea : tareas) {
                resultados.add(tarea.get());
            }
            return resultados;
        } finally {
            hilos.shutdownNow();
        }
    }

    private long saldo() {
        return jdbc.queryForObject("SELECT saldo FROM fidelizacion.saldo_puntos WHERE cliente_id = ?", Long.class, CLIENTE);
    }

    private String nivel() {
        return jdbc.queryForObject("SELECT nivel FROM fidelizacion.saldo_puntos WHERE cliente_id = ?", String.class, CLIENTE);
    }

    private long puntosDe(String idTransaccion) {
        return jdbc.queryForObject("SELECT puntos FROM fidelizacion.movimiento_puntos "
                + "WHERE id_transaccion = ? AND tipo_movimiento = 'REVERSA'", Long.class, idTransaccion);
    }

    private long cambiosDeNivel() {
        return jdbc.queryForObject("SELECT count(*) FROM fidelizacion.cambio_nivel WHERE cliente_id = ?", Long.class, CLIENTE);
    }

    private static String anulacion(String idAnulacion, String tipo, String monto) {
        return anulacion(idAnulacion, "V-100234", tipo, monto);
    }

    private static String anulacion(String idAnulacion, String idCompra, String tipo, String monto) {
        return """
                {
                  "idEvento": "%s",
                  "tipoEvento": "COMPRA_ANULADA",
                  "fechaEmision": "2026-10-25T09:15:00-04:00",
                  "anulacion": {
                    "idAnulacion": "%s",
                    "idCompra": "%s",
                    "idCliente": "CLI-5521",
                    "tipo": "%s",
                    "fecha": "2026-10-25T09:12:00-04:00",
                    "montoRevertido": %s,
                    "motivo": "Prueba de reversa"
                  }
                }
                """.formatted(UUID.randomUUID(), idAnulacion, idCompra, tipo, monto);
    }

    private static String acumulacion(String idTransaccion, String valorConsumido, OffsetDateTime fecha) {
        return """
                {
                  "idEvento": "%s",
                  "tipoEvento": "MOVIMIENTO_PUNTOS",
                  "fechaEmision": "%s",
                  "movimiento": {
                    "idTransaccion": "%s",
                    "idCliente": "CLI-5521",
                    "tipoMovimiento": "ACUMULACION",
                    "valorConsumido": %s,
                    "fecha": "%s"
                  }
                }
                """.formatted(UUID.randomUUID(), fecha, idTransaccion, valorConsumido, fecha);
    }
}
