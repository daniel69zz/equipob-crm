package com.maxiconecta.crm.segmentacion.asignacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxiconecta.crm.segmentacion.criterio.Criterio;
import com.maxiconecta.crm.segmentacion.criterio.Criterio.Condicion;
import com.maxiconecta.crm.segmentacion.criterio.Criterio.Indicador;
import com.maxiconecta.crm.segmentacion.criterio.CriterioService;
import com.maxiconecta.crm.segmentacion.criterio.SolicitudCriterio;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-266 · Asignación automática de segmento con mensajes de métricas simulados publicados en
 * RabbitMQ, como los que emite servicio-comportamiento tras una compra, una reentrega o una
 * anulación. PostgreSQL y RabbitMQ reales.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AsignacionSegmentoIntegracionTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    private static final AtomicLong CLIENTES = new AtomicLong(1000);
    private static final OffsetDateTime INICIO = OffsetDateTime.of(2026, 10, 13, 12, 0, 0, 0, ZoneOffset.UTC);

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registro) {
        // Las migraciones reales del Gateway son dueñas de la auditoría compartida.
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("seguridad").locations("filesystem:../api-gateway/src/main/resources/db/migration").load()
                .migrate();
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
        registro.add("spring.rabbitmq.listener.simple.retry.initial-interval", () -> "100ms");
    }

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private AmqpAdmin admin;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CriterioService criterios;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private MockMvc mvc;

    private Criterio habituales;
    private Criterio frecuentes;
    private Criterio altoValor;
    private Criterio lectores;

    @BeforeEach
    void prepararCriterios() {
        admin.purgeQueue(ConfiguracionRabbit.COLA_METRICAS_RESPALDO, false);
        jdbc.execute("TRUNCATE segmentacion.segmento_cliente, segmentacion.metricas_cliente, segmentacion.evento_procesado");
        jdbc.update("DELETE FROM segmentacion.criterio");
        habituales = crear("Habituales", Indicador.FRECUENCIA, Condicion.ENTRE, "2", "9", null);
        frecuentes = crear("Frecuentes", Indicador.FRECUENCIA, Condicion.MAYOR_IGUAL, "10", null, null);
        altoValor = crear("Alto valor", Indicador.VALOR, Condicion.MAYOR_IGUAL, "5000", null, null);
        lectores = crear("Lectores", Indicador.CATEGORIA, Condicion.CONTIENE, null, null, "Libros");
    }

    // --- Criterio 1: una compra confirmada asigna el segmento y lo guarda en el perfil ---

    @Test
    void unaCompraAsignaElSegmentoYLoGuardaEnElPerfil() throws Exception {
        long cliente = CLIENTES.incrementAndGet();
        String evento = publicarYEsperar(cliente, 1, "120.00", INICIO, "Libros");

        assertThat(segmento(cliente)).isEqualTo("Lectores");
        assertThat(jdbc.queryForMap("SELECT criterio_id, indicador, id_evento_origen FROM segmentacion.segmento_cliente "
                + "WHERE cliente_id = ?", cliente))
                .containsEntry("criterio_id", lectores.id()).containsEntry("indicador", "CATEGORIA")
                .containsEntry("id_evento_origen", evento);
        mvc.perform(get("/api/segmentacion/clientes/" + cliente + "/segmento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segmento").value("Lectores"))
                .andExpect(jsonPath("$.sinDatos").value(false));
    }

    @Test
    void cadaCompraNuevaActualizaElSegmento() {
        long cliente = CLIENTES.incrementAndGet();
        publicarYEsperar(cliente, 1, "120.00", INICIO, "Libros");
        publicarYEsperar(cliente, 2, "240.00", INICIO.plusMinutes(1), "Libros");
        assertThat(segmento(cliente)).isEqualTo("Habituales");
        publicarYEsperar(cliente, 10, "1500.00", INICIO.plusMinutes(2), "Libros");
        assertThat(segmento(cliente)).isEqualTo("Frecuentes");
    }

    // --- Criterio 2: con varios criterios se aplica la regla de prioridad ---

    @Test
    void conVariosCriteriosSeAplicaLaPrioridadValorFrecuenciaCategoria() {
        long cliente = CLIENTES.incrementAndGet();
        publicarYEsperar(cliente, 12, "6200.00", INICIO, "Libros");

        assertThat(segmento(cliente)).isEqualTo("Alto valor");
        assertThat(jdbc.queryForObject("SELECT criterio_id FROM segmentacion.segmento_cliente WHERE cliente_id = ?",
                Long.class, cliente)).isEqualTo(altoValor.id());
    }

    // --- Criterio 3: un evento reentregado no recalcula el segmento dos veces ---

    @Test
    void unEventoReentregadoNoRecalculaElSegmento() {
        long cliente = CLIENTES.incrementAndGet();
        String idEvento = UUID.randomUUID().toString();
        publicar(metricas(idEvento, cliente, 2, "240.00", INICIO, "Libros"));
        esperarEvento(idEvento);
        OffsetDateTime asignado = asignadoEn(cliente);

        // La reentrega trae otras métricas a propósito: si se recalculara, cambiaría a Frecuentes.
        publicar(metricas(idEvento, cliente, 10, "1500.00", INICIO.plusMinutes(1), "Libros"));
        // Los mensajes se consumen en orden: cuando se aplica este, la reentrega ya se procesó.
        publicarYEsperar(CLIENTES.incrementAndGet(), 1, "10.00", INICIO, "Hogar");

        assertThat(segmento(cliente)).isEqualTo("Habituales");
        assertThat(asignadoEn(cliente)).isEqualTo(asignado);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM segmentacion.evento_procesado WHERE id_evento = ?",
                Long.class, idEvento)).isEqualTo(1);
        assertThat(cambiosAuditados(cliente)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM segmentacion.historial_segmento WHERE cliente_id=?",
                Long.class, cliente)).isEqualTo(1);
    }

    // --- Criterio 4: al anular una compra el segmento se reevalúa y cambia si corresponde ---

    @Test
    void unaAnulacionReevaluaYCambiaElSegmento() {
        long cliente = CLIENTES.incrementAndGet();
        publicarYEsperar(cliente, 10, "1500.00", INICIO, "Libros");
        assertThat(segmento(cliente)).isEqualTo("Frecuentes");

        publicarAnulacionYEsperar(cliente, 9, "1300.00", INICIO.plusMinutes(1), "Libros");
        assertThat(segmento(cliente)).isEqualTo("Habituales");
    }

    @Test
    void siYaNoCumpleNingunCriterioSeRetiraElSegmento() throws Exception {
        long cliente = CLIENTES.incrementAndGet();
        publicarYEsperar(cliente, 1, "80.00", INICIO, "Libros");
        publicarAnulacionYEsperar(cliente, 0, "0", INICIO.plusMinutes(1));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM segmentacion.segmento_cliente WHERE cliente_id = ?",
                Long.class, cliente)).isZero();
        mvc.perform(get("/api/segmentacion/clientes/" + cliente + "/segmento"))
                .andExpect(jsonPath("$.sinDatos").value(true));
    }

    @Test
    void sinCriteriosQueCumplirNoSeAsignaNiSeAudita() {
        long cliente = CLIENTES.incrementAndGet();
        publicarYEsperar(cliente, 1, "50.00", INICIO, "Hogar");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM segmentacion.segmento_cliente WHERE cliente_id = ?",
                Long.class, cliente)).isZero();
        assertThat(jdbc.queryForObject("SELECT frecuencia FROM segmentacion.metricas_cliente WHERE cliente_id = ?",
                Long.class, cliente)).isEqualTo(1);
        assertThat(cambiosAuditados(cliente)).isEmpty();
    }

    // --- Orden de los mensajes ---

    @Test
    void unMensajeCalculadoAntesQueLaFotoVigenteNoCambiaElSegmento() {
        long cliente = CLIENTES.incrementAndGet();
        publicarYEsperar(cliente, 10, "1500.00", INICIO.plusMinutes(5), "Libros");
        String atrasado = publicarYEsperar(cliente, 2, "240.00", INICIO, "Libros");

        assertThat(segmento(cliente)).isEqualTo("Frecuentes");
        assertThat(jdbc.queryForObject("SELECT resultado FROM segmentacion.evento_procesado WHERE id_evento = ?",
                String.class, atrasado)).isEqualTo("DESACTUALIZADO");
    }

    // --- Auditoría (SCRUM-265) ---

    @Test
    void cadaCambioDeSegmentoQuedaAuditadoConElCriterioYLasMetricas() throws Exception {
        long cliente = CLIENTES.incrementAndGet();
        publicarYEsperar(cliente, 1, "80.00", INICIO, "Libros");
        publicarYEsperar(cliente, 1, "90.00", INICIO.plusMinutes(1), "Libros");
        String cambio = publicarYEsperar(cliente, 2, "200.00", INICIO.plusMinutes(2), "Libros");
        publicarAnulacionYEsperar(cliente, 0, "0", INICIO.plusMinutes(3));

        List<Map<String, Object>> auditados = cambiosAuditados(cliente);
        assertThat(auditados).extracting(fila -> fila.get("operacion"))
                .containsExactly("SEGMENTO_ASIGNADO", "SEGMENTO_CAMBIADO", "SEGMENTO_RETIRADO");
        assertThat(auditados).allSatisfy(fila -> assertThat(fila.get("usuario")).isEqualTo("sistema"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM segmentacion.historial_segmento WHERE cliente_id=?",
                Long.class, cliente)).isEqualTo(3);

        JsonNode detalle = json.readTree((String) auditados.get(1).get("detalle"));
        assertThat(detalle.at("/anterior/segmento").asText()).isEqualTo("Lectores");
        assertThat(detalle.at("/nuevo/segmento").asText()).isEqualTo("Habituales");
        assertThat(detalle.at("/nuevo/criterioId").asLong()).isEqualTo(habituales.id());
        assertThat(detalle.at("/metricas/frecuencia").asLong()).isEqualTo(2);
        assertThat(detalle.at("/idEvento").asText()).isEqualTo(cambio);
        assertThat(json.readTree((String) auditados.get(2).get("detalle")).get("nuevo").isNull()).isTrue();
    }

    // --- Mensajes que no se pueden procesar: cola de respaldo y reproceso ---

    @Test
    void unMensajeInvalidoVaALaColaDeRespaldoYSePuedeReinyectar() throws Exception {
        publicar("{ esto no es json");
        await().atMost(Duration.ofSeconds(15)).until(() -> mensajesEnRespaldo() == 1);

        mvc.perform(post("/api/segmentacion/respaldo/reinyectar")
                        .header("X-Usuario", "admin").header("X-Usuario-Rol", "ADMINISTRADOR_CRM")
                        .header("X-Usuario-Permisos", "SEGMENTACION_CONFIGURAR"))
                .andExpect(status().isOk());
        // Sigue siendo inválido: vuelve a respaldo, sin perderse.
        await().atMost(Duration.ofSeconds(15)).until(() -> mensajesEnRespaldo() == 1);

        mvc.perform(post("/api/segmentacion/respaldo/reinyectar").header("X-Usuario", "ana")
                        .header("X-Usuario-Permisos", "SEGMENTOS_CONSULTAR"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unMensajeIncompletoVaALaColaDeRespaldoSinTocarElSegmento() {
        long cliente = CLIENTES.incrementAndGet();
        Map<String, Object> sinCliente = datos(UUID.randomUUID().toString(), cliente, 1, "80.00", INICIO,
                "COMPRA_CONFIRMADA", "Libros");
        sinCliente.remove("idCliente");
        publicar(escribir(sinCliente));

        await().atMost(Duration.ofSeconds(15)).until(() -> mensajesEnRespaldo() == 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM segmentacion.metricas_cliente", Long.class)).isZero();
    }

    @Test
    void conLaColaDeRespaldoVaciaElReprocesoNoHaceNada() throws Exception {
        mvc.perform(post("/api/segmentacion/respaldo/reinyectar")
                        .header("X-Usuario", "admin").header("X-Usuario-Permisos", "SEGMENTACION_CONFIGURAR"))
                .andExpect(status().isNoContent());
    }

    // --- Utilidades ---

    private String publicarYEsperar(long cliente, long frecuencia, String valor, OffsetDateTime calculadas,
                                    String... categorias) {
        return publicarYEsperarTipo("COMPRA_CONFIRMADA", cliente, frecuencia, valor, calculadas, categorias);
    }

    private String publicarAnulacionYEsperar(long cliente, long frecuencia, String valor, OffsetDateTime calculadas,
                                             String... categorias) {
        return publicarYEsperarTipo("COMPRA_ANULADA", cliente, frecuencia, valor, calculadas, categorias);
    }

    private String publicarYEsperarTipo(String tipo, long cliente, long frecuencia, String valor,
                                        OffsetDateTime calculadas, String... categorias) {
        String idEvento = UUID.randomUUID().toString();
        publicar(escribir(datos(idEvento, cliente, frecuencia, valor, calculadas, tipo, categorias)));
        esperarEvento(idEvento);
        return idEvento;
    }

    private String metricas(String idEvento, long cliente, long frecuencia, String valor, OffsetDateTime calculadas,
                            String... categorias) {
        return escribir(datos(idEvento, cliente, frecuencia, valor, calculadas, "COMPRA_CONFIRMADA", categorias));
    }

    private static Map<String, Object> datos(String idEvento, long cliente, long frecuencia, String valor,
                                             OffsetDateTime calculadas, String tipo, String... categorias) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idEvento", idEvento);
        datos.put("tipoEventoOrigen", tipo);
        datos.put("idCliente", cliente);
        datos.put("identificadoresOrigen", List.of("CLI-" + cliente));
        datos.put("frecuencia", frecuencia);
        datos.put("valor", new BigDecimal(valor));
        datos.put("categorias", List.of(categorias));
        datos.put("calculadasEn", calculadas.toString());
        return datos;
    }

    private String escribir(Map<String, Object> datos) {
        try {
            return json.writeValueAsString(datos);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void publicar(String contenido) {
        rabbit.send(ConfiguracionRabbit.EXCHANGE_CRM, ConfiguracionRabbit.RUTA_METRICAS_ACTUALIZADAS,
                MessageBuilder.withBody(contenido.getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON).build());
    }

    private void esperarEvento(String idEvento) {
        await().atMost(Duration.ofSeconds(15)).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM segmentacion.evento_procesado WHERE id_evento = ?", Long.class, idEvento) == 1);
    }

    private String segmento(long cliente) {
        return jdbc.queryForObject("SELECT segmento FROM segmentacion.segmento_cliente WHERE cliente_id = ?",
                String.class, cliente);
    }

    private OffsetDateTime asignadoEn(long cliente) {
        return jdbc.queryForObject("SELECT asignado_en FROM segmentacion.segmento_cliente WHERE cliente_id = ?",
                OffsetDateTime.class, cliente);
    }

    private List<Map<String, Object>> cambiosAuditados(long cliente) {
        return jdbc.queryForList("""
                SELECT usuario, operacion, detalle FROM auditoria.evento
                WHERE entidad = 'SEGMENTO_CLIENTE' AND entidad_id = ? ORDER BY id
                """, Long.toString(cliente));
    }

    private int mensajesEnRespaldo() {
        var propiedades = admin.getQueueProperties(ConfiguracionRabbit.COLA_METRICAS_RESPALDO);
        return propiedades == null ? -1 : ((Number) propiedades.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).intValue();
    }

    private Criterio crear(String nombre, Indicador indicador, Condicion condicion, String minimo, String maximo,
                           String categoria) {
        return criterios.crear(new SolicitudCriterio(nombre, "", indicador, condicion,
                minimo == null ? null : new BigDecimal(minimo), maximo == null ? null : new BigDecimal(maximo),
                categoria, null), "admin");
    }
}
