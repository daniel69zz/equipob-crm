package com.maxiconecta.crm.comportamiento.cambio;

import com.maxiconecta.crm.comportamiento.compra.ClientePerfiles;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-320 · Detección de cambios de comportamiento (SCRUM-34, docs/compra/cambios-comportamiento.md)
 * con PostgreSQL real e historiales simulados: caída, aumento y comportamiento estable, cliente sin
 * línea base, anulaciones, compras con fecha futura, no duplicación de alertas y la consulta.
 * Valores por defecto: ventana de 90 días, línea base de 3 periodos (los días 90 a 360 antes de la
 * evaluación), 3 compras mínimas y umbral de 50 %.
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CambiosComportamientoIntegracionTest {

    private static final String RUTA = "/api/comportamiento/clientes/42/cambios-comportamiento";
    private static final Instant REFERENCIA = Instant.parse("2026-10-10T12:00:00Z");

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
    private DetectorCambiosComportamiento detector;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE comportamiento.deteccion_cambio_comportamiento, "
                + "comportamiento.estado_comportamiento_cliente, comportamiento.cliente_nivel_consumo, "
                + "comportamiento.cliente_inactivo, comportamiento.frecuencia_compra, "
                + "comportamiento.intento_reproceso, comportamiento.evento_procesado, comportamiento.anulacion_item, "
                + "comportamiento.anulacion, comportamiento.compra_item, comportamiento.compra, "
                + "comportamiento.evento_recibido");
        when(perfiles.buscar(anyString())).thenReturn(Optional.empty());
        evaluarEl(REFERENCIA);
    }

    // --- Criterio 1: una variación relevante se registra con su tipo y los valores comparados ---

    @Test
    void unClienteQueDejaDeComprarComoAntesGeneraUnaAlertaDeCaida() throws Exception {
        // Línea base: 12 compras de 100 en 270 días (4 por periodo); periodo actual: 1 compra de 100.
        historialHabitual("CLI-1", 12, "100.00");
        comprar("CLI-1", 20, "100.00");

        DetectorCambiosComportamiento.ResultadoDeteccion resultado = detector.detectarTodos();

        assertThat(resultado.deteccionesNuevas()).isEqualTo(2);
        assertThat(estado("CLI-1")).isEqualTo("CON_VARIACION");
        mvc.perform(get(RUTA).param("identificador", "CLI-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CON_VARIACION"))
                .andExpect(jsonPath("$.evaluadoEn").value("2026-10-10T12:00:00Z"))
                .andExpect(jsonPath("$.alertas.length()").value(2))
                .andExpect(jsonPath("$.alertas[?(@.tipo == 'FRECUENCIA')].direccion").value("DISMINUCION"))
                .andExpect(jsonPath("$.alertas[?(@.tipo == 'FRECUENCIA')].valorActual").value(1.00))
                .andExpect(jsonPath("$.alertas[?(@.tipo == 'FRECUENCIA')].valorHabitual").value(4.00))
                .andExpect(jsonPath("$.alertas[?(@.tipo == 'FRECUENCIA')].variacion").value(-0.75))
                .andExpect(jsonPath("$.alertas[?(@.tipo == 'FRECUENCIA')].umbral").value(0.5))
                .andExpect(jsonPath("$.alertas[?(@.tipo == 'FRECUENCIA')].periodoDesde").value("2026-07-12T12:00:00Z"))
                .andExpect(jsonPath("$.alertas[?(@.tipo == 'FRECUENCIA')].periodoHasta").value("2026-10-10T12:00:00Z"))
                .andExpect(jsonPath("$.alertas[?(@.tipo == 'FRECUENCIA')].mensaje").value(
                        "La frecuencia de compra bajó un 75 % respecto a lo habitual (1 frente a 4 por periodo de 90 días)"))
                .andExpect(jsonPath("$.alertas[?(@.tipo == 'MONTO')].direccion").value("DISMINUCION"))
                .andExpect(jsonPath("$.alertas[?(@.tipo == 'MONTO')].valorActual").value(100.00))
                .andExpect(jsonPath("$.alertas[?(@.tipo == 'MONTO')].valorHabitual").value(400.00));
    }

    @Test
    void unClienteQueCompraMuchoMasQueLoHabitualGeneraUnaAlertaDeAumento() {
        historialHabitual("CLI-1", 3, "100.00");
        comprar("CLI-1", 10, "100.00");
        comprar("CLI-1", 20, "100.00");
        comprar("CLI-1", 30, "100.00");

        detector.detectarTodos();

        assertThat(detecciones("CLI-1", "FRECUENCIA", "AUMENTO")).isEqualTo(1);
        assertThat(detecciones("CLI-1", "MONTO", "AUMENTO")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT variacion FROM comportamiento.deteccion_cambio_comportamiento "
                + "WHERE id_cliente_origen = 'CLI-1' AND tipo = 'FRECUENCIA'", Double.class)).isEqualTo(2.0);
    }

    @Test
    void unComportamientoEstableNoGeneraAlertas() throws Exception {
        historialHabitual("CLI-1", 6, "100.00");
        comprar("CLI-1", 15, "110.00");
        comprar("CLI-1", 60, "95.00");

        DetectorCambiosComportamiento.ResultadoDeteccion resultado = detector.detectarTodos();

        assertThat(resultado.deteccionesNuevas()).isZero();
        assertThat(estado("CLI-1")).isEqualTo("ESTABLE");
        mvc.perform(get(RUTA).param("identificador", "CLI-1"))
                .andExpect(jsonPath("$.estado").value("ESTABLE"))
                .andExpect(jsonPath("$.alertas.length()").value(0));
    }

    @Test
    void soloSeDetectaElMontoCuandoLaFrecuenciaSeMantiene() {
        historialHabitual("CLI-1", 6, "100.00");
        comprar("CLI-1", 15, "20.00");
        comprar("CLI-1", 60, "20.00");

        detector.detectarTodos();

        assertThat(detecciones("CLI-1", "FRECUENCIA", "DISMINUCION")).isZero();
        assertThat(detecciones("CLI-1", "MONTO", "DISMINUCION")).isEqualTo(1);
    }

    // --- Criterio 2: con historial insuficiente queda "sin línea base" y no se detecta nada ---

    @Test
    void unClienteConPocoHistorialQuedaSinLineaBaseYSinAlertas() throws Exception {
        comprar("CLI-NUEVO", 200, "100.00");
        comprar("CLI-NUEVO", 150, "100.00");

        DetectorCambiosComportamiento.ResultadoDeteccion resultado = detector.detectarTodos();

        assertThat(resultado.sinLineaBase()).isEqualTo(1);
        assertThat(resultado.deteccionesNuevas()).isZero();
        assertThat(estado("CLI-NUEVO")).isEqualTo("SIN_LINEA_BASE");
        mvc.perform(get(RUTA).param("identificador", "CLI-NUEVO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SIN_LINEA_BASE"))
                .andExpect(jsonPath("$.alertas.length()").value(0));
    }

    @Test
    void unClienteQueRecienEmpiezaAComprarNoTieneLineaBaseAunqueCompreSeguido() {
        for (int dia = 5; dia <= 80; dia += 15) {
            comprar("CLI-RECIENTE", dia, "100.00");
        }

        detector.detectarTodos();

        assertThat(estado("CLI-RECIENTE")).isEqualTo("SIN_LINEA_BASE");
        assertThat(detecciones("CLI-RECIENTE", "FRECUENCIA", "AUMENTO")).isZero();
    }

    @Test
    void unClienteNoEvaluadoNoEsUnError() throws Exception {
        mvc.perform(get(RUTA).param("identificador", "CLI-DESCONOCIDO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SIN_EVALUAR"))
                .andExpect(jsonPath("$.alertas.length()").value(0));
        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SIN_EVALUAR"));
    }

    // --- Criterio 4: la misma variación no se duplica dentro del periodo ---

    @Test
    void volverAEvaluarElMismoDiaOAlDiaSiguienteNoDuplicaLaAlerta() {
        historialHabitual("CLI-1", 12, "100.00");
        comprar("CLI-1", 20, "100.00");

        detector.detectarTodos();
        DetectorCambiosComportamiento.ResultadoDeteccion repetida = detector.detectarTodos();
        evaluarEl(REFERENCIA.plus(1, ChronoUnit.DAYS));
        DetectorCambiosComportamiento.ResultadoDeteccion alDiaSiguiente = detector.detectarTodos();

        assertThat(repetida.deteccionesNuevas()).isZero();
        assertThat(alDiaSiguiente.deteccionesNuevas()).isZero();
        assertThat(detecciones("CLI-1", "FRECUENCIA", "DISMINUCION")).isEqualTo(1);
        assertThat(detecciones("CLI-1", "MONTO", "DISMINUCION")).isEqualTo(1);
        assertThat(estado("CLI-1")).isEqualTo("CON_VARIACION");
    }

    @Test
    void pasadoElPeriodoLaMismaVariacionSeVuelveARegistrar() {
        historialHabitual("CLI-1", 12, "100.00");
        detector.detectarTodos();
        assertThat(detecciones("CLI-1", "FRECUENCIA", "DISMINUCION")).isEqualTo(1);

        // 91 días después la línea base sigue teniendo compras habituales y el cliente sigue sin comprar.
        for (int dia = 91; dia <= 180; dia += 30) {
            comprar("CLI-1", dia, "100.00");
        }
        evaluarEl(REFERENCIA.plus(91, ChronoUnit.DAYS));
        detector.detectarTodos();

        assertThat(detecciones("CLI-1", "FRECUENCIA", "DISMINUCION")).isEqualTo(2);
    }

    // --- Datos históricos (SCRUM-318): solo cuenta lo vigente y hasta el momento de la evaluación ---

    @Test
    void unaCompraAnuladaDelPeriodoActualNoCuenta() {
        historialHabitual("CLI-1", 6, "100.00");
        comprar("CLI-1", 15, "100.00");
        String anulada = comprar("CLI-1", 60, "100.00");
        anularTotal(anulada, "CLI-1", "100.00");

        detector.detectarTodos();

        assertThat(detecciones("CLI-1", "FRECUENCIA", "DISMINUCION")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT valor_actual FROM comportamiento.deteccion_cambio_comportamiento "
                + "WHERE id_cliente_origen = 'CLI-1' AND tipo = 'FRECUENCIA'", Double.class)).isEqualTo(1.0);
    }

    @Test
    void unaCompraConFechaFuturaNoInflaElPeriodoActual() {
        historialHabitual("CLI-1", 6, "100.00");
        comprar("CLI-1", 15, "100.00");
        comprar("CLI-1", 60, "100.00");
        comprar("CLI-1", -10, "900.00");

        detector.detectarTodos();

        assertThat(estado("CLI-1")).isEqualTo("ESTABLE");
    }

    @Test
    void unClienteQueYaNoTieneComprasVigentesDejaDeTenerEstadoPeroConservaSusDetecciones() {
        String unica = comprar("CLI-1", 300, "100.00");
        historialHabitual("CLI-2", 12, "100.00");
        detector.detectarTodos();
        assertThat(estado("CLI-1")).isEqualTo("SIN_LINEA_BASE");
        long deteccionesAntes = jdbc.queryForObject(
                "SELECT count(*) FROM comportamiento.deteccion_cambio_comportamiento", Long.class);

        anularTotal(unica, "CLI-1", "100.00");
        detector.detectarTodos();

        assertThat(estado("CLI-1")).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comportamiento.deteccion_cambio_comportamiento",
                Long.class)).isEqualTo(deteccionesAntes);
    }

    // --- Un cliente con perfiles unificados ve juntas las alertas de todos sus identificadores ---

    @Test
    void laConsultaCombinaLosIdentificadoresDelCliente() throws Exception {
        historialHabitual("CLI-1", 12, "100.00");
        historialHabitual("mp-user-1", 6, "100.00");
        comprar("mp-user-1", 15, "100.00");
        comprar("mp-user-1", 60, "100.00");
        comprarAlgo("CLI-SOLO-NUEVO", 10);

        detector.detectarTodos();

        mvc.perform(get(RUTA).param("identificador", "CLI-1", "mp-user-1"))
                .andExpect(jsonPath("$.estado").value("CON_VARIACION"))
                .andExpect(jsonPath("$.alertas.length()").value(2))
                .andExpect(jsonPath("$.alertas[0].idClienteOrigen").value("CLI-1"));
        mvc.perform(get(RUTA).param("identificador", "mp-user-1", "CLI-SOLO-NUEVO"))
                .andExpect(jsonPath("$.estado").value("ESTABLE"));
    }

    // --- Utilidades ---

    private void evaluarEl(Instant instante) {
        when(reloj.instant()).thenReturn(instante);
        when(reloj.getZone()).thenReturn(ZoneOffset.UTC);
    }

    /**
     * {@code cantidad} compras repartidas en toda la línea base: la primera justo al inicio (360 días
     * antes de la evaluación) y la última más de 90 días antes.
     */
    private void historialHabitual(String idCliente, int cantidad, String monto) {
        for (int i = 0; i < cantidad; i++) {
            comprar(idCliente, 360 - (260 * i) / cantidad, monto);
        }
    }

    private void comprarAlgo(String idCliente, int diasAntes) {
        comprar(idCliente, diasAntes, "50.00");
    }

    private String comprar(String idCliente, int diasAntesDeLaEvaluacion, String monto) {
        String idCompra = "V-" + UUID.randomUUID();
        String fecha = REFERENCIA.minus(diasAntesDeLaEvaluacion, ChronoUnit.DAYS).toString();
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
                    "items": [ { "categoria": "Electrónica", "cantidad": 1, "monto": %s } ]
                  }
                }
                """.formatted(UUID.randomUUID(), fecha, idCompra, idCliente, fecha, monto, monto));
        exigirProcesados();
        return idCompra;
    }

    private void anularTotal(String idCompra, String idCliente, String monto) {
        String fecha = REFERENCIA.minus(1, ChronoUnit.DAYS).toString();
        ingesta.recibirAnulacion("""
                {
                  "idEvento": "%s",
                  "tipoEvento": "COMPRA_ANULADA",
                  "fechaEmision": "%s",
                  "anulacion": {
                    "idAnulacion": "%s-A",
                    "idCompra": "%s",
                    "idCliente": "%s",
                    "tipo": "TOTAL",
                    "fecha": "%s",
                    "montoRevertido": %s,
                    "motivo": "Prueba de cambios de comportamiento"
                  }
                }
                """.formatted(UUID.randomUUID(), fecha, idCompra, idCompra, idCliente, fecha, monto));
        exigirProcesados();
    }

    private void exigirProcesados() {
        assertThat(eventos.findAll()).allMatch(e -> e.getEstado() == EstadoEvento.PROCESADO);
    }

    private String estado(String idClienteOrigen) {
        return jdbc.query("SELECT estado FROM comportamiento.estado_comportamiento_cliente WHERE id_cliente_origen = ?",
                rs -> rs.next() ? rs.getString(1) : null, idClienteOrigen);
    }

    private long detecciones(String idClienteOrigen, String tipo, String direccion) {
        return jdbc.queryForObject("SELECT count(*) FROM comportamiento.deteccion_cambio_comportamiento "
                + "WHERE id_cliente_origen = ? AND tipo = ? AND direccion = ?", Long.class, idClienteOrigen, tipo,
                direccion);
    }
}
