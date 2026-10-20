package com.maxiconecta.crm.comportamiento.riesgo;

import com.maxiconecta.crm.comportamiento.cambio.DetectorCambiosComportamiento;
import com.maxiconecta.crm.comportamiento.compra.ClientePerfiles;
import com.maxiconecta.crm.comportamiento.ingesta.EstadoEvento;
import com.maxiconecta.crm.comportamiento.ingesta.EventoRecibidoRepository;
import com.maxiconecta.crm.comportamiento.ingesta.IngestaCompras;
import com.maxiconecta.crm.comportamiento.mensajeria.CanalEventos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-323 · Riesgo de abandono (SCRUM-35, docs/compra/riesgo-abandono.md) con PostgreSQL real e
 * historiales simulados que llegan como compras de Marketplace y Ventas: cliente activo, en descenso
 * e inactivo; una caída detectada por SCRUM-34 que sube el riesgo; una compra nueva que lo baja en el
 * momento; el paso de los días; anulaciones; varios identificadores del mismo perfil; y las consultas.
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RiesgoAbandonoIntegracionTest {

    private static final Instant REFERENCIA = Instant.parse("2026-10-20T12:00:00Z");

    /** Perfil del CRM al que se vincula cada identificador de Marketplace y Ventas. */
    private static final Map<String, Long> PERFILES = Map.of(
            "CLI-ACTIVO", 101L, "CLI-DESCENSO", 102L, "CLI-INACTIVO", 103L,
            "CLI-VARIOS", 104L, "mp-varios", 104L, "CLI-ANULADO", 105L);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private ClientePerfiles perfiles;

    @MockBean
    private Clock reloj;

    /** Las métricas para la segmentación no salen del servicio en esta prueba. */
    @MockBean
    private CanalEventos canal;

    @Autowired
    private IngestaCompras ingesta;

    @Autowired
    private EventoRecibidoRepository eventos;

    @Autowired
    private CalculadoraRiesgoAbandono calculadora;

    @Autowired
    private DetectorCambiosComportamiento detector;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE comportamiento.riesgo_abandono_cliente, comportamiento.deteccion_cambio_comportamiento, "
                + "comportamiento.estado_comportamiento_cliente, comportamiento.cliente_nivel_consumo, "
                + "comportamiento.cliente_inactivo, comportamiento.frecuencia_compra, "
                + "comportamiento.intento_reproceso, comportamiento.evento_procesado, comportamiento.anulacion_item, "
                + "comportamiento.anulacion, comportamiento.compra_item, comportamiento.compra, "
                + "comportamiento.evento_recibido");
        when(perfiles.buscar(anyString())).thenAnswer(invocacion ->
                Optional.ofNullable(PERFILES.get(invocacion.<String>getArgument(0))));
        evaluarEl(REFERENCIA);
    }

    // --- Criterio 1: con indicadores calculados el cliente obtiene un puntaje y un nivel ---

    @Test
    void unClienteActivoTieneRiesgoBajo() throws Exception {
        clienteActivo();

        // Cada compra recalcula el riesgo al confirmarse, sin esperar la ejecución programada.
        assertThat(riesgo(101)).containsEntry("nivel", "BAJO").containsEntry("compras", 12L)
                .containsEntry("clasificacion_frecuencia", "FRECUENTE").containsEntry("nivel_consumo", "ALTO")
                .containsEntry("dias_sin_comprar", 3).containsEntry("en_seguimiento_desde", null);
        assertThat(puntaje(101)).isEqualTo("1.67");
        mvc.perform(get("/api/comportamiento/clientes/101/riesgo-abandono"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivel").value("BAJO"))
                .andExpect(jsonPath("$.puntaje").value(1.67))
                .andExpect(jsonPath("$.sinDatos").value(false));
    }

    @Test
    void unClienteEnDescensoTieneRiesgoMedio() {
        clienteEnDescenso();

        assertThat(riesgo(102)).containsEntry("nivel", "MEDIO").containsEntry("compras", 7L)
                .containsEntry("clasificacion_frecuencia", "HABITUAL").containsEntry("nivel_consumo", "MEDIO")
                .containsEntry("cambio_detectado", false);
        assertThat(puntaje(102)).isEqualTo("52.78");
    }

    @Test
    void unClienteInactivoTieneRiesgoAltoYQuedaEnSeguimiento() {
        clienteInactivo();

        assertThat(riesgo(103)).containsEntry("nivel", "ALTO").containsEntry("inactivo", true)
                .containsEntry("dias_sin_comprar", 120);
        assertThat(puntaje(103)).isEqualTo("100.00");
        assertThat(jdbc.queryForObject("SELECT en_seguimiento_desde IS NOT NULL FROM "
                + "comportamiento.riesgo_abandono_cliente WHERE cliente_id = 103", Boolean.class)).isTrue();
    }

    // --- Criterio 2: el riesgo se actualiza con una detección, con una compra y con el paso del tiempo ---

    @Test
    void unaCaidaDetectadaSubeElRiesgoDelClienteEnDescensoAAlto() throws Exception {
        clienteEnDescenso();
        assertThat(riesgo(102)).containsEntry("nivel", "MEDIO");

        detector.detectarTodos();
        CalculadoraRiesgoAbandono.ResultadoRecalculo resultado = calculadora.recalcularTodos();

        assertThat(resultado.nuevosEnRiesgoAlto()).isEqualTo(1);
        assertThat(riesgo(102)).containsEntry("nivel", "ALTO").containsEntry("cambio_detectado", true);
        assertThat(puntaje(102)).isEqualTo("67.78");
        mvc.perform(get("/api/comportamiento/clientes/102/riesgo-abandono"))
                .andExpect(jsonPath("$.nivel").value("ALTO"))
                .andExpect(jsonPath("$.puntaje").value(67.78))
                .andExpect(jsonPath("$.enSeguimientoDesde").value("2026-10-20T12:00:00Z"))
                .andExpect(jsonPath("$.factores[*].factor",
                        contains("RECENCIA", "FRECUENCIA", "VALOR", "CAMBIO_COMPORTAMIENTO")))
                .andExpect(jsonPath("$.factores[0].puntos").value(27.78))
                .andExpect(jsonPath("$.factores[0].maximo").value(50))
                .andExpect(jsonPath("$.factores[0].descripcion")
                        .value("50 días sin comprar (se considera inactivo después de 90)"))
                .andExpect(jsonPath("$.factores[1].descripcion").value("Habitual: 7 compras vigentes"))
                .andExpect(jsonPath("$.factores[2].descripcion").value("Nivel de consumo medio: Bs 1400.00 acumulados"))
                .andExpect(jsonPath("$.factores[3].puntos").value(15))
                .andExpect(jsonPath("$.factores[3].descripcion")
                        .value("Bajó su frecuencia o su monto de compra en los últimos 90 días"));
    }

    @Test
    void unaCompraNuevaBajaElRiesgoAlMomentoYSaleDelSeguimiento() {
        clienteInactivo();
        assertThat(riesgo(103)).containsEntry("nivel", "ALTO");

        comprar("CLI-INACTIVO", 0, "200.00");

        // 3 compras (ocasional, 25) y Bs 500 (medio, 12,5), compradas hoy (0): 37,5.
        assertThat(riesgo(103)).containsEntry("nivel", "MEDIO").containsEntry("inactivo", false)
                .containsEntry("en_seguimiento_desde", null);
        assertThat(puntaje(103)).isEqualTo("37.50");
    }

    @Test
    void conElPasoDeLosDiasSinComprarElRiesgoSube() {
        clienteActivo();
        evaluarEl(REFERENCIA.plus(60, ChronoUnit.DAYS));

        calculadora.recalcularTodos();

        // 63 días sin comprar: 50 × 63 / 90 = 35, justo el umbral del riesgo medio.
        assertThat(riesgo(101)).containsEntry("nivel", "MEDIO").containsEntry("dias_sin_comprar", 63);
        assertThat(puntaje(101)).isEqualTo("35.00");
    }

    @Test
    void unClienteAlQueSeLeAnulaTodoQuedaSinRiesgo() throws Exception {
        String idCompra = comprar("CLI-ANULADO", 10, "300.00");
        assertThat(riesgo(105)).isNotNull();

        anularTotal(idCompra, "CLI-ANULADO", "300.00");

        assertThat(riesgo(105)).isNull();
        mvc.perform(get("/api/comportamiento/clientes/105/riesgo-abandono"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinDatos").value(true))
                .andExpect(jsonPath("$.factores.length()").value(0));
    }

    @Test
    void lasComprasDeVariosIdentificadoresDelMismoPerfilSeSumanEnUnSoloRiesgo() {
        comprar("CLI-VARIOS", 40, "100.00");
        comprar("CLI-VARIOS", 30, "100.00");
        comprar("mp-varios", 20, "100.00");
        comprar("mp-varios", 10, "100.00");

        assertThat(riesgo(104)).containsEntry("compras", 4L).containsEntry("clasificacion_frecuencia", "HABITUAL")
                .containsEntry("dias_sin_comprar", 10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comportamiento.riesgo_abandono_cliente", Long.class))
                .isEqualTo(1);
    }

    @Test
    void unaCompraSinPerfilVinculadoNoTieneRiesgo() {
        comprar("CLI-SIN-PERFIL", 5, "100.00");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM comportamiento.riesgo_abandono_cliente", Long.class))
                .isZero();
    }

    // --- Criterio 3: los clientes en riesgo alto quedan identificados para seguimiento ---

    @Test
    void elListadoPorNivelOrdenaDelRiesgoMasAltoAlMasBajo() throws Exception {
        clienteActivo();
        clienteEnDescenso();
        clienteInactivo();
        detector.detectarTodos();
        calculadora.recalcularTodos();

        mvc.perform(get("/api/comportamiento/clientes").param("riesgoAbandono", "alto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.umbrales.medio").value(35))
                .andExpect(jsonPath("$.umbrales.alto").value(65))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.content[*].clienteId", contains(103, 102)))
                .andExpect(jsonPath("$.content[0].inactivo").value(true))
                .andExpect(jsonPath("$.content[1].cambioDetectado").value(true))
                .andExpect(jsonPath("$.content[1].enSeguimientoDesde").value("2026-10-20T12:00:00Z"));
        mvc.perform(get("/api/comportamiento/clientes").param("riesgoAbandono", "BAJO"))
                .andExpect(jsonPath("$.content[*].clienteId", contains(101)));
        mvc.perform(get("/api/comportamiento/clientes").param("riesgoAbandono", "EXTREMO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unClienteSinRiesgoCalculadoRespondeSinDatos() throws Exception {
        mvc.perform(get("/api/comportamiento/clientes/999/riesgo-abandono"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(999))
                .andExpect(jsonPath("$.sinDatos").value(true));
    }

    // --- Historiales simulados ---

    /** 12 compras de 500 (frecuente, Bs 6.000: consumo alto); la última hace 3 días. */
    private void clienteActivo() {
        for (int i = 0; i < 12; i++) {
            comprar("CLI-ACTIVO", 3 + 25 * i, "500.00");
        }
    }

    /**
     * 6 compras de 200 en la línea base de SCRUM-34 (de 350 a 150 días atrás) y 1 hace 50 días: habitual,
     * Bs 1.400 (consumo medio). Frente a lo habitual (unas 2 compras por periodo de 90 días) es una caída
     * de más del 50 % en frecuencia y en monto.
     */
    private void clienteEnDescenso() {
        for (int dias = 350; dias >= 150; dias -= 40) {
            comprar("CLI-DESCENSO", dias, "200.00");
        }
        comprar("CLI-DESCENSO", 50, "200.00");
    }

    /** 2 compras de 150 (ocasional, Bs 300: consumo bajo); la última hace 120 días. */
    private void clienteInactivo() {
        comprar("CLI-INACTIVO", 200, "150.00");
        comprar("CLI-INACTIVO", 120, "150.00");
    }

    // --- Utilidades ---

    private void evaluarEl(Instant instante) {
        when(reloj.instant()).thenReturn(instante);
        when(reloj.getZone()).thenReturn(ZoneOffset.UTC);
    }

    private String comprar(String idCliente, int diasAntesDeLaReferencia, String monto) {
        String idCompra = "V-" + UUID.randomUUID();
        String fecha = REFERENCIA.minus(diasAntesDeLaReferencia, ChronoUnit.DAYS).toString();
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
                    "motivo": "Prueba del riesgo de abandono"
                  }
                }
                """.formatted(UUID.randomUUID(), fecha, idCompra, idCompra, idCliente, fecha, monto));
        exigirProcesados();
    }

    private void exigirProcesados() {
        assertThat(eventos.findAll()).allMatch(e -> e.getEstado() == EstadoEvento.PROCESADO);
    }

    private Map<String, Object> riesgo(long clienteId) {
        return jdbc.query("SELECT * FROM comportamiento.riesgo_abandono_cliente WHERE cliente_id = ?",
                rs -> rs.next() ? new ColumnMapRowMapper().mapRow(rs, 0) : null,
                clienteId);
    }

    private String puntaje(long clienteId) {
        return jdbc.queryForObject("SELECT puntaje::text FROM comportamiento.riesgo_abandono_cliente WHERE cliente_id = ?",
                String.class, clienteId);
    }
}
