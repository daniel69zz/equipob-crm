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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-592 · Acumulación de puntos (SCRUM-45, docs/fidelizacion/acumulacion-puntos.md) con eventos
 * simulados RIO-CRM-03 publicados en RabbitMQ, PostgreSQL real y el servicio de Perfil simulado:
 * acumulación, reentrega, eventos inválidos, reproceso, detalle por transacción y concurrencia.
 * CLI-5521 y mp-user-3307 son el cliente 42 del CRM.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AcumulacionPuntosIntegracionTest {

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
        when(perfiles.buscar(anyString())).thenReturn(Optional.of(CLIENTE));
    }

    // --- Criterio 1: los puntos se calculan con la regla y se suman al saldo ---

    @Test
    void unaAcumulacionPublicadaEnRabbitSumaLosPuntosAlSaldo() throws Exception {
        publicar(ejemplo("movimiento-acumulacion-ana.json"));

        assertThat(esperar(1).get(0).estado()).isEqualTo(Estado.PROCESADO);
        assertThat(saldo()).isEqualTo(35);
        mvc.perform(get("/api/fidelizacion/clientes/42/puntos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(35))
                .andExpect(jsonPath("$.sinDatos").value(false));
    }

    @Test
    void cadaAcumulacionSeSumaAlSaldoAnterior() {
        publicar(ejemplo("movimiento-acumulacion-ana.json"));
        publicar(ejemplo("movimiento-acumulacion-carlos.json"));

        assertThat(esperar(2)).allMatch(e -> e.estado() == Estado.PROCESADO);
        assertThat(saldo()).isEqualTo(35 + 12);
    }

    @Test
    void unaTransaccionMenorQueUnTramoQuedaRegistradaConCeroPuntos() {
        ingesta.recibir(acumulacion("V-CHICA", "9.99"));

        assertThat(saldo()).isZero();
        assertThat(jdbc.queryForObject("SELECT puntos FROM fidelizacion.movimiento_puntos WHERE id_transaccion = 'V-CHICA'",
                Long.class)).isZero();
    }

    // --- Criterio 2: un evento reentregado no acredita puntos dos veces ---

    @Test
    void unEventoReentregadoNoAcreditaDosVeces() {
        publicar(ejemplo("movimiento-acumulacion-ana.json"));
        esperar(1);
        publicar(ejemplo("movimiento-acumulacion-ana.json"));

        List<EventoMovimiento> eventos = esperar(2);
        assertThat(eventos).extracting(EventoMovimiento::estado)
                .containsExactlyInAnyOrder(Estado.PROCESADO, Estado.DESCARTADO);
        assertThat(eventos).filteredOn(e -> e.estado() == Estado.DESCARTADO).singleElement()
                .extracting(EventoMovimiento::causa).asString().contains("ya acreditó");
        assertThat(saldo()).isEqualTo(35);
        assertThat(movimientos()).isEqualTo(1);
    }

    @Test
    void laMismaTransaccionConOtroIdEventoTampocoSeAcreditaDosVeces() {
        ingesta.recibir(acumulacion("V-1", "100.00"));
        ingesta.recibir(acumulacion("V-1", "100.00"));

        assertThat(saldo()).isEqualTo(10);
        assertThat(bitacora.listar(Estado.DESCARTADO, 10)).hasSize(1);
    }

    @Test
    void dosCopiasSimultaneasDeLaMismaTransaccionAcreditanUnaSolaVez() throws Exception {
        String contenido = acumulacion("V-CONCURRENTE", "500.00");
        List<Estado> resultados = enParalelo(contenido, contenido, contenido, contenido);

        assertThat(resultados).containsOnlyOnce(Estado.PROCESADO);
        assertThat(saldo()).isEqualTo(50);
    }

    @Test
    void acumulacionesSimultaneasDeDistintasTransaccionesNoSePisan() throws Exception {
        List<Estado> resultados = enParalelo(acumulacion("V-A", "100.00"), acumulacion("V-B", "200.00"),
                acumulacion("V-C", "300.00"), acumulacion("V-D", "400.00"));

        assertThat(resultados).containsOnly(Estado.PROCESADO);
        assertThat(saldo()).isEqualTo(10 + 20 + 30 + 40);
    }

    // --- Criterio 3: un evento inválido queda fallido con la causa y el saldo no cambia ---

    @Test
    void unEventoInvalidoQuedaFallidoConSuCausaYSuContenidoSinCambiarElSaldo() {
        ingesta.recibir(acumulacion("V-1", "100.00"));
        String contenido = ejemplo("movimiento-invalido.json");
        publicar(contenido);

        EventoMovimiento fallido = esperar(2).stream().filter(e -> e.estado() == Estado.FALLIDO).findFirst().orElseThrow();
        assertThat(fallido.causa()).contains("movimiento.valorConsumido");
        assertThat(fallido.contenido()).isEqualTo(contenido);
        assertThat(fallido.idTransaccion()).isEqualTo("V-100999");
        assertThat(saldo()).isEqualTo(10);
    }

    @Test
    void unMensajeIlegibleQuedaFallidoConElTextoOriginal() {
        publicar("esto no es json");

        EventoMovimiento evento = esperar(1).get(0);
        assertThat(evento.estado()).isEqualTo(Estado.FALLIDO);
        assertThat(evento.contenido()).isEqualTo("esto no es json");
        assertThat(saldoExiste()).isFalse();
    }

    @Test
    void unaRedencionQuedaFallidaHastaQueSeProceseEnSuHistoria() {
        ingesta.recibir(ejemplo("movimiento-redencion.json"));

        EventoMovimiento evento = bitacora.listar(null, 10).get(0);
        assertThat(evento.estado()).isEqualTo(Estado.FALLIDO);
        assertThat(evento.causa()).contains("REDENCION").contains("SCRUM-48");
        assertThat(saldoExiste()).isFalse();
    }

    @Test
    void unClienteSinPerfilQuedaFallidoYSePuedeReprocesarCuandoSeVincula() throws Exception {
        when(perfiles.buscar("CLI-5521")).thenReturn(Optional.empty());
        ingesta.recibir(ejemplo("movimiento-acumulacion-ana.json"));
        EventoMovimiento fallido = bitacora.listar(Estado.FALLIDO, 10).get(0);
        assertThat(fallido.causa()).contains("no tiene perfil en el CRM");

        when(perfiles.buscar("CLI-5521")).thenReturn(Optional.of(CLIENTE));
        mvc.perform(post("/api/fidelizacion/eventos/" + fallido.id() + "/reprocesar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PROCESADO"))
                .andExpect(jsonPath("$.intentos").value(2));
        assertThat(saldo()).isEqualTo(35);

        mvc.perform(post("/api/fidelizacion/eventos/" + fallido.id() + "/reprocesar"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/fidelizacion/eventos/999999/reprocesar")).andExpect(status().isNotFound());
    }

    @Test
    void siPerfilNoRespondeElEventoQuedaFallidoParaReproceso() throws Exception {
        when(perfiles.buscar(anyString())).thenThrow(new ClientesPerfil.PerfilNoDisponibleException("Perfil no responde"));
        ingesta.recibir(ejemplo("movimiento-acumulacion-ana.json"));

        mvc.perform(get("/api/fidelizacion/eventos").param("estado", "FALLIDO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].causa").value(org.hamcrest.Matchers.containsString("Perfil no responde")));
        assertThat(saldoExiste()).isFalse();
    }

    // --- Criterio 4: queda el detalle de puntos por transacción ---

    @Test
    void cadaTransaccionAcreditadaDejaSuDetalleConElSaldoResultante() throws Exception {
        ingesta.recibir(ejemplo("movimiento-acumulacion-ana.json"));
        ingesta.recibir(ejemplo("movimiento-acumulacion-carlos.json"));

        mvc.perform(get("/api/fidelizacion/clientes/42/movimientos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].idTransaccion").value("MP-88120"))
                .andExpect(jsonPath("$[0].idClienteOrigen").value("mp-user-3307"))
                .andExpect(jsonPath("$[0].valorConsumido").value(129.90))
                .andExpect(jsonPath("$[0].puntos").value(12))
                .andExpect(jsonPath("$[0].bolivianosPorPunto").value(10.00))
                .andExpect(jsonPath("$[0].saldoResultante").value(47))
                .andExpect(jsonPath("$[1].idTransaccion").value("V-100234"))
                .andExpect(jsonPath("$[1].puntos").value(35))
                .andExpect(jsonPath("$[1].saldoResultante").value(35));
    }

    // --- Utilidades ---

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
            List<Future<Estado>> tareas = java.util.Arrays.stream(contenidos)
                    .map(c -> hilos.submit(() -> {
                        salida.await();
                        return ingesta.recibir(c);
                    })).toList();
            salida.countDown();
            List<Estado> resultados = new java.util.ArrayList<>();
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

    private boolean saldoExiste() {
        return jdbc.queryForObject("SELECT count(*) FROM fidelizacion.saldo_puntos", Long.class) > 0;
    }

    private long movimientos() {
        return jdbc.queryForObject("SELECT count(*) FROM fidelizacion.movimiento_puntos", Long.class);
    }

    private static String acumulacion(String idTransaccion, String valor) {
        return """
                {
                  "idEvento": "%s",
                  "tipoEvento": "MOVIMIENTO_PUNTOS",
                  "fechaEmision": "2026-10-18T10:15:05-04:00",
                  "movimiento": {
                    "idTransaccion": "%s",
                    "idCliente": "CLI-5521",
                    "tipoMovimiento": "ACUMULACION",
                    "valorConsumido": %s,
                    "fecha": "2026-10-18T10:14:50-04:00"
                  }
                }
                """.formatted(UUID.randomUUID(), idTransaccion, valor);
    }
}
