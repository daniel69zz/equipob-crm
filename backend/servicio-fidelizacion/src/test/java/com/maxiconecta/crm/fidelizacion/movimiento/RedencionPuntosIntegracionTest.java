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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-48 · Redención de puntos (docs/fidelizacion/redencion-puntos.md) con eventos simulados
 * RIO-CRM-03 publicados en RabbitMQ, PostgreSQL real y el servicio de Perfil simulado: redención,
 * saldo insuficiente, reentrega, reevaluación del nivel (SCRUM-49), confirmación, reproceso y concurrencia.
 * CLI-5521 es el cliente 42 del CRM; su acumulación V-100234 le da 35 puntos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RedencionPuntosIntegracionTest {

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
        jdbc.execute("TRUNCATE fidelizacion.movimiento_puntos, fidelizacion.evento_movimiento, fidelizacion.saldo_puntos");
        // Niveles iniciales de SCRUM-49 (Bronce 0–499): los 35 puntos de Ana son BRONCE.
        jdbc.update("DELETE FROM fidelizacion.umbral_nivel WHERE version > 1");
        jdbc.update("DELETE FROM fidelizacion.configuracion_niveles WHERE version > 1");
        when(perfiles.buscar(anyString())).thenReturn(Optional.of(CLIENTE));
    }

    // --- Criterio 1: una redención válida descuenta los puntos y deja el movimiento con su transacción ---

    @Test
    void unaRedencionPublicadaEnRabbitDescuentaLosPuntosDelSaldo() throws Exception {
        acumularLos35DeAna();
        publicar(ejemplo("movimiento-redencion.json"));

        assertThat(esperar(2)).allMatch(e -> e.estado() == Estado.PROCESADO);
        assertThat(saldo()).isEqualTo(15);
        mvc.perform(get("/api/fidelizacion/clientes/42/puntos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(15));
    }

    @Test
    void laRedencionQuedaEnElHistorialConSuTransaccionDeOrigen() throws Exception {
        acumularLos35DeAna();
        ingesta.recibir(ejemplo("movimiento-redencion.json"));

        mvc.perform(get("/api/fidelizacion/clientes/42/movimientos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].idTransaccion").value("R-5001"))
                .andExpect(jsonPath("$[0].tipoMovimiento").value("REDENCION"))
                .andExpect(jsonPath("$[0].idClienteOrigen").value("CLI-5521"))
                .andExpect(jsonPath("$[0].puntos").value(20))
                .andExpect(jsonPath("$[0].variacionSaldo").value(-20))
                .andExpect(jsonPath("$[0].valorConsumido").value(20.00))
                .andExpect(jsonPath("$[0].bolivianosPorPunto").value(nullValue()))
                .andExpect(jsonPath("$[0].versionRegla").value(nullValue()))
                .andExpect(jsonPath("$[0].saldoResultante").value(15))
                .andExpect(jsonPath("$[1].idTransaccion").value("V-100234"))
                .andExpect(jsonPath("$[1].variacionSaldo").value(35))
                .andExpect(jsonPath("$[1].versionRegla").value(1));
    }

    @Test
    void unaRedencionSinValorConsumidoTambienSeAplica() {
        acumularLos35DeAna();

        assertThat(ingesta.recibir(redencion("R-SIN-VALOR", 35))).isEqualTo(Estado.PROCESADO);
        assertThat(saldo()).isZero();
        assertThat(jdbc.queryForObject("SELECT valor_consumido FROM fidelizacion.movimiento_puntos "
                + "WHERE id_transaccion = 'R-SIN-VALOR'", java.math.BigDecimal.class)).isNull();
    }

    // --- Criterio 2: una redención mayor al saldo queda fallida con la causa y el saldo no cambia ---

    @Test
    void unaRedencionMayorAlSaldoQuedaFallidaConLaCausaYElSaldoNoCambia() {
        acumularLos35DeAna();
        String contenido = ejemplo("movimiento-redencion-saldo-insuficiente.json");
        publicar(contenido);

        EventoMovimiento fallido = esperar(2).stream().filter(e -> e.estado() == Estado.FALLIDO).findFirst().orElseThrow();
        assertThat(fallido.causa()).contains("Saldo insuficiente").contains("R-5002")
                .contains("se solicitan 500 puntos").contains("saldo disponible es 35");
        assertThat(fallido.contenido()).isEqualTo(contenido);
        assertThat(saldo()).isEqualTo(35);
        assertThat(redenciones()).isZero();
    }

    @Test
    void unClienteSinSaldoNoPuedeRedimir() {
        assertThat(ingesta.recibir(redencion("R-1", 1))).isEqualTo(Estado.FALLIDO);

        assertThat(bitacora.listar(Estado.FALLIDO, 10).get(0).causa()).contains("saldo disponible es 0");
        assertThat(saldoExiste()).isFalse();
        assertThat(redenciones()).isZero();
    }

    @Test
    void unaRedencionFallidaPorSaldoSePuedeReprocesarCuandoElClienteAcumula() throws Exception {
        ingesta.recibir(ejemplo("movimiento-redencion.json"));
        EventoMovimiento fallido = bitacora.listar(Estado.FALLIDO, 10).get(0);

        acumularLos35DeAna();
        mvc.perform(post("/api/fidelizacion/eventos/" + fallido.id() + "/reprocesar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PROCESADO"))
                .andExpect(jsonPath("$.intentos").value(2));
        assertThat(saldo()).isEqualTo(15);
    }

    @Test
    void unaRedencionSinPuntosQuedaFallidaConElCampo() {
        acumularLos35DeAna();
        String sinPuntos = ejemplo("movimiento-redencion.json").replace("\"puntos\": 20,", "");

        assertThat(ingesta.recibir(sinPuntos)).isEqualTo(Estado.FALLIDO);
        assertThat(bitacora.listar(Estado.FALLIDO, 10).get(0).causa()).contains("movimiento.puntos");
        assertThat(saldo()).isEqualTo(35);
    }

    // --- Criterio 3: un evento de redención reentregado no descuenta dos veces ---

    @Test
    void unaRedencionReentregadaNoDescuentaDosVeces() {
        acumularLos35DeAna();
        publicar(ejemplo("movimiento-redencion.json"));
        esperar(2);
        publicar(ejemplo("movimiento-redencion.json"));

        List<EventoMovimiento> redenciones = esperar(3).stream().filter(e -> "REDENCION".equals(e.tipoMovimiento())).toList();
        assertThat(redenciones).extracting(EventoMovimiento::estado)
                .containsExactlyInAnyOrder(Estado.PROCESADO, Estado.DESCARTADO);
        assertThat(redenciones).filteredOn(e -> e.estado() == Estado.DESCARTADO).singleElement()
                .extracting(EventoMovimiento::causa).asString().contains("ya descontó");
        assertThat(saldo()).isEqualTo(15);
        assertThat(redenciones()).isEqualTo(1);
    }

    @Test
    void laMismaRedencionConOtroIdEventoTampocoSeDescuentaDosVeces() {
        acumularLos35DeAna();
        ingesta.recibir(redencion("R-1", 10));
        ingesta.recibir(redencion("R-1", 10));

        assertThat(saldo()).isEqualTo(25);
        assertThat(bitacora.listar(Estado.DESCARTADO, 10)).hasSize(1);
    }

    @Test
    void dosCopiasSimultaneasDeLaMismaRedencionDescuentanUnaSolaVez() throws Exception {
        acumularLos35DeAna();
        String contenido = redencion("R-CONCURRENTE", 10);
        List<Estado> resultados = enParalelo(contenido, contenido, contenido, contenido);

        assertThat(resultados).containsOnlyOnce(Estado.PROCESADO);
        assertThat(saldo()).isEqualTo(25);
    }

    @Test
    void redencionesSimultaneasNoDejanElSaldoNegativo() throws Exception {
        acumularLos35DeAna();
        List<Estado> resultados = enParalelo(redencion("R-A", 10), redencion("R-B", 10), redencion("R-C", 10),
                redencion("R-D", 10));

        assertThat(resultados).filteredOn(e -> e == Estado.PROCESADO).hasSize(3);
        assertThat(resultados).filteredOn(e -> e == Estado.FALLIDO).hasSize(1);
        assertThat(saldo()).isEqualTo(5);
    }

    // --- Criterio 4: después de la redención el cliente queda en el nivel de su nuevo saldo ---

    @Test
    void trasLaRedencionElClienteQuedaEnElNivelDeSuNuevoSaldo() throws Exception {
        plataDesde30Puntos();
        acumularLos35DeAna();
        assertThat(nivel()).isEqualTo("PLATA");

        ingesta.recibir(ejemplo("movimiento-redencion.json"));

        assertThat(saldo()).isEqualTo(15);
        assertThat(nivel()).isEqualTo("BRONCE");
        assertThat(puntosPeriodo()).isEqualTo(15);
        mvc.perform(get("/api/fidelizacion/clientes/42/puntos"))
                .andExpect(jsonPath("$.saldo").value(15))
                .andExpect(jsonPath("$.nivel").value("BRONCE"));
    }

    @Test
    void conLosNivelesInicialesUnaRedencionBajaDePlataABronce() {
        ingesta.recibir(acumulacion("V-PLATA", "6000.00"));
        assertThat(nivel()).isEqualTo("PLATA");

        ingesta.recibir(redencion("R-200", 200));

        assertThat(saldo()).isEqualTo(400);
        assertThat(nivel()).isEqualTo("BRONCE");
    }

    @Test
    void laRedencionReevaluaElNivelConLaConfiguracionVigente() {
        acumularLos35DeAna();
        assertThat(nivel()).isEqualTo("BRONCE");
        plataDesde30Puntos();

        ingesta.recibir(redencion("R-1", 1));

        assertThat(nivel()).isEqualTo("PLATA");
    }

    @Test
    void unaRedencionFallidaNoCambiaElNivel() {
        plataDesde30Puntos();
        acumularLos35DeAna();

        ingesta.recibir(ejemplo("movimiento-redencion-saldo-insuficiente.json"));

        assertThat(nivel()).isEqualTo("PLATA");
        assertThat(puntosPeriodo()).isEqualTo(35);
    }

    // --- SCRUM-406: confirmación de la redención por su transacción de origen ---

    @Test
    void laConfirmacionDeUnaRedencionAplicadaTraeElSaldoYElNivel() throws Exception {
        acumularLos35DeAna();
        ingesta.recibir(ejemplo("movimiento-redencion.json"));

        mvc.perform(get("/api/fidelizacion/redenciones/R-5001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APLICADA"))
                .andExpect(jsonPath("$.idClienteOrigen").value("CLI-5521"))
                .andExpect(jsonPath("$.puntos").value(20))
                .andExpect(jsonPath("$.saldoResultante").value(15))
                .andExpect(jsonPath("$.nivel").value("BRONCE"))
                .andExpect(jsonPath("$.causa").value(nullValue()));
    }

    @Test
    void laConfirmacionDeUnaRedencionFallidaTraeLaCausaYElEventoParaReprocesar() throws Exception {
        acumularLos35DeAna();
        ingesta.recibir(ejemplo("movimiento-redencion-saldo-insuficiente.json"));
        long evento = bitacora.listar(Estado.FALLIDO, 10).get(0).id();

        mvc.perform(get("/api/fidelizacion/redenciones/R-5002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FALLIDA"))
                .andExpect(jsonPath("$.causa").value(containsString("Saldo insuficiente")))
                .andExpect(jsonPath("$.idEvento").value(evento))
                .andExpect(jsonPath("$.saldoResultante").value(nullValue()));
    }

    @Test
    void unaRedencionQueNuncaLlegoNoSeEncuentra() throws Exception {
        mvc.perform(get("/api/fidelizacion/redenciones/R-NO-EXISTE")).andExpect(status().isNotFound());
    }

    // --- SCRUM-408: la bitácora se filtra por estado y tipo de movimiento ---

    @Test
    void laBitacoraFiltraLasRedencionesFallidas() throws Exception {
        ingesta.recibir(ejemplo("movimiento-invalido.json"));
        ingesta.recibir(redencion("R-1", 10));

        mvc.perform(get("/api/fidelizacion/eventos").param("estado", "FALLIDO").param("tipo", "REDENCION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idTransaccion").value("R-1"));
        mvc.perform(get("/api/fidelizacion/eventos").param("tipo", "REGALO")).andExpect(status().isBadRequest());
    }

    // --- Utilidades ---

    private void acumularLos35DeAna() {
        assertThat(ingesta.recibir(ejemplo("movimiento-acumulacion-ana.json"))).isEqualTo(Estado.PROCESADO);
    }

    private void publicar(String contenido) {
        rabbit.send(ConfiguracionRabbit.EXCHANGE_VENTAS, ConfiguracionRabbit.RUTA_MOVIMIENTO_PUNTOS,
                new Message(contenido.getBytes(StandardCharsets.UTF_8)));
    }

    private List<EventoMovimiento> esperar(int cantidad) {
        await().atMost(Duration.ofSeconds(15)).until(() -> bitacora.listar(null, 100).stream()
                .filter(e -> e.estado() != Estado.RECIBIDO).count() == cantidad);
        return bitacora.listar(null, 100);
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

    /** Versión 2 de los niveles, sin recalcular a nadie: Bronce 0–29, Plata 30–1.999, Oro desde 2.000. */
    private void plataDesde30Puntos() {
        jdbc.update("INSERT INTO fidelizacion.configuracion_niveles (version, periodo_meses, modificado_por) "
                + "VALUES (2, 12, 'prueba')");
        jdbc.update("INSERT INTO fidelizacion.umbral_nivel (version, nivel, puntos_minimos, puntos_maximos) VALUES "
                + "(2, 'BRONCE', 0, 29), (2, 'PLATA', 30, 1999), (2, 'ORO', 2000, NULL)");
    }

    private long puntosPeriodo() {
        return jdbc.queryForObject("SELECT puntos_periodo FROM fidelizacion.saldo_puntos WHERE cliente_id = ?",
                Long.class, CLIENTE);
    }

    private static String acumulacion(String idTransaccion, String valorConsumido) {
        return """
                {
                  "idEvento": "%s",
                  "tipoEvento": "MOVIMIENTO_PUNTOS",
                  "fechaEmision": "2026-10-18T12:00:00-04:00",
                  "movimiento": {
                    "idTransaccion": "%s",
                    "idCliente": "CLI-5521",
                    "tipoMovimiento": "ACUMULACION",
                    "valorConsumido": %s,
                    "fecha": "2026-10-18T11:59:40-04:00"
                  }
                }
                """.formatted(UUID.randomUUID(), idTransaccion, valorConsumido);
    }

    private boolean saldoExiste() {
        return jdbc.queryForObject("SELECT count(*) FROM fidelizacion.saldo_puntos", Long.class) > 0;
    }

    private long redenciones() {
        return jdbc.queryForObject("SELECT count(*) FROM fidelizacion.movimiento_puntos WHERE tipo_movimiento = 'REDENCION'",
                Long.class);
    }

    private static String redencion(String idTransaccion, long puntos) {
        return """
                {
                  "idEvento": "%s",
                  "tipoEvento": "MOVIMIENTO_PUNTOS",
                  "fechaEmision": "2026-10-18T13:00:00-04:00",
                  "movimiento": {
                    "idTransaccion": "%s",
                    "idCliente": "CLI-5521",
                    "tipoMovimiento": "REDENCION",
                    "puntos": %d,
                    "fecha": "2026-10-18T12:59:40-04:00"
                  }
                }
                """.formatted(UUID.randomUUID(), idTransaccion, puntos);
    }
}
