package com.maxiconecta.crm.comportamiento.preferencia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxiconecta.crm.comportamiento.compra.ClientePerfiles;
import com.maxiconecta.crm.comportamiento.compra.Identificador;
import com.maxiconecta.crm.comportamiento.configuracion.ConfiguracionRabbit;
import com.maxiconecta.crm.comportamiento.ingesta.EventoRecibidoRepository;
import com.maxiconecta.crm.comportamiento.metrica.RepublicacionMetricas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.maxiconecta.crm.comportamiento.ingesta.LectorEventosTest.ejemplo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-371 · Actualización del perfil de preferencias con los eventos simulados de Marketplace y
 * Ventas publicados en RabbitMQ (compra, reentrega y anulación), con PostgreSQL y RabbitMQ reales
 * (docs/preferencias/actualizacion-por-eventos.md).
 * <p>
 * V-100234 (CLI-5521): Electrónica 300.00 + Accesorios 50.50. MP-88120 (mp-user-3307): 129.90.
 * Ambos identificadores son del cliente 42 del CRM.
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.retry.initial-interval=100ms")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ActualizacionPreferenciasIntegracionTest {

    private static final long CLIENTE = 42;
    private static final String RUTA = "/api/comportamiento/clientes/42/preferencias";
    private static final String COMPRA_ANA = "5b7a8c1e-3f2d-4e6a-9b1c-2d3e4f5a6b7c";
    private static final String DEVOLUCION_ANA = "8d2f6a4c-1b3e-4c5d-9e7f-0a1b2c3d4e5f";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @MockBean
    private ClientePerfiles perfiles;

    @MockBean
    private IdentificadoresPreferencias identificadores;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private AmqpAdmin admin;

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
        jdbc.execute("TRUNCATE comportamiento.frecuencia_compra, comportamiento.intento_reproceso, "
                + "comportamiento.evento_procesado, comportamiento.anulacion_item, comportamiento.anulacion, "
                + "comportamiento.compra_item, comportamiento.compra, comportamiento.evento_recibido, "
                + "comportamiento.actualizacion_preferencias, comportamiento.perfil_preferencias CASCADE");
        admin.purgeQueue(ConfiguracionRabbit.COLA_PREFERENCIAS_RESPALDO, false);
        when(perfiles.buscar(anyString())).thenReturn(Optional.of(CLIENTE));
        when(identificadores.obtener(CLIENTE))
                .thenReturn(List.of(new Identificador("CLI-5521"), new Identificador("mp-user-3307")));
    }

    // --- Criterio 1: una compra confirmada actualiza el perfil ---

    @Test
    void unaCompraConfirmadaActualizaElPerfil() throws Exception {
        publicar("compra-ana.json");
        esperarVersion(1);

        mvc.perform(get(RUTA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.construido").value(true))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.categorias[*].categoria").value(contains("Electrónica", "Accesorios")))
                .andExpect(jsonPath("$.origen").value("COMPRA_CONFIRMADA"))
                .andExpect(jsonPath("$.idEventoOrigen").value(COMPRA_ANA));

        publicar("compra-carlos.json");
        esperarVersion(2);
        assertThat(categorias()).containsExactly("Electrónica", "Hogar", "Accesorios", "Limpieza");
    }

    // --- Criterio 2: al anular una compra el perfil deja de considerarla ---

    @Test
    void unaDevolucionYUnaAnulacionDejanDeConsiderarseEnElPerfil() throws Exception {
        publicar("compra-ana.json");
        esperarVersion(1);

        publicar("anulacion-parcial.json");
        esperarVersion(2);
        assertThat(categorias()).containsExactly("Electrónica");
        mvc.perform(get(RUTA)).andExpect(jsonPath("$.origen").value("COMPRA_ANULADA"))
                .andExpect(jsonPath("$.idEventoOrigen").value(DEVOLUCION_ANA));

        publicar("anulacion-total.json");
        esperarVersion(3);
        assertThat(categorias()).isEmpty();
        mvc.perform(get(RUTA)).andExpect(jsonPath("$.sinPreferencias").value(true));
    }

    // --- Criterio 3: un evento reentregado no actualiza el perfil dos veces ---

    @Test
    void unEventoDeVentaReentregadoNoActualizaDosVeces() {
        publicar("compra-ana.json");
        esperarVersion(1);
        publicar("compra-ana.json");

        await().atMost(Duration.ofSeconds(15)).until(() -> eventos.count() == 2);
        publicar("compra-carlos.json");
        esperarVersion(2);
        assertThat(actualizaciones()).hasSize(2);
    }

    @Test
    void unMensajeDeMetricasReentregadoNoActualizaDosVeces() throws Exception {
        String mensaje = metricas(COMPRA_ANA, CLIENTE, "COMPRA_CONFIRMADA");
        publicarMetricas(mensaje);
        esperarVersion(1);
        publicarMetricas(mensaje);
        // Mensaje de control: cuando se aplica, la reentrega ya se procesó (una sola cola, en orden).
        publicarMetricas(metricas("control-1", CLIENTE, "COMPRA_CONFIRMADA"));
        esperarVersion(2);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM comportamiento.actualizacion_preferencias "
                + "WHERE id_evento = ?", Long.class, COMPRA_ANA)).isEqualTo(1);
    }

    // --- Criterio 4: cada actualización queda registrada con su fecha y su evento ---

    @Test
    void cadaActualizacionQuedaRegistradaConSuFechaYSuEvento() throws Exception {
        publicar("compra-ana.json");
        esperarVersion(1);
        publicar("anulacion-parcial.json");
        esperarVersion(2);

        mvc.perform(get(RUTA + "/actualizaciones")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].idEvento").value(DEVOLUCION_ANA))
                .andExpect(jsonPath("$[0].tipoEventoOrigen").value("COMPRA_ANULADA"))
                .andExpect(jsonPath("$[0].versionAnterior").value(1))
                .andExpect(jsonPath("$[0].versionNueva").value(2))
                .andExpect(jsonPath("$[0].categoriasAnteriores").value(contains("Electrónica", "Accesorios")))
                .andExpect(jsonPath("$[0].categoriasNuevas").value(contains("Electrónica")))
                .andExpect(jsonPath("$[0].actualizadoEn").isNotEmpty())
                .andExpect(jsonPath("$[1].idEvento").value(COMPRA_ANA))
                .andExpect(jsonPath("$[1].versionAnterior").value(0));
    }

    // --- No se modifica en otros estados ---

    @Test
    void unEventoFallidoNoModificaElPerfil() {
        publicar("compra-sin-cliente.json");
        // Control: cuando la compra válida actualiza el perfil, el evento fallido ya se procesó.
        publicar("compra-carlos.json");
        esperarVersion(1);

        assertThat(actualizaciones()).singleElement()
                .satisfies(fila -> assertThat(fila.get("id_evento")).isEqualTo("c3d4e5f6-7a8b-4c9d-8e1f-a2b3c4d5e6f7"));
    }

    @Test
    void sinElServicioDePerfilElMensajeQuedaEnRespaldoSinTocarElPerfil() {
        when(identificadores.obtener(CLIENTE)).thenThrow(new PreferenciasException(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo verificar el cliente en el servicio de perfiles; intente nuevamente"));
        publicarMetricas(metricas(COMPRA_ANA, CLIENTE, "COMPRA_CONFIRMADA"));

        await().atMost(Duration.ofSeconds(15)).until(() -> mensajesEnRespaldo() == 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comportamiento.perfil_preferencias", Long.class))
                .isZero();
    }

    @Test
    void lasMetricasRepublicadasSeConfirmanSinTocarElPerfil() throws Exception {
        publicarMetricas(metricas("recalculo-1", CLIENTE, RepublicacionMetricas.TIPO));
        // Control: cuando se aplica, la republicación ya se procesó (una sola cola, en orden).
        publicarMetricas(metricas("control-1", CLIENTE, "COMPRA_CONFIRMADA"));
        esperarVersion(1);

        assertThat(actualizaciones()).singleElement()
                .satisfies(fila -> assertThat(fila.get("id_evento")).isEqualTo("control-1"));
        assertThat(mensajesEnRespaldo()).isZero();
    }

    @Test
    void unMensajeInvalidoVaDirectoALaColaDeRespaldo() {
        publicarMetricas("{ no es json");
        publicarMetricas(metricas(COMPRA_ANA, CLIENTE, "OTRO_TIPO"));

        await().atMost(Duration.ofSeconds(15)).until(() -> mensajesEnRespaldo() == 2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comportamiento.actualizacion_preferencias", Long.class))
                .isZero();
    }

    // --- Utilidades ---

    private void publicar(String archivo) {
        String ruta = archivo.startsWith("anulacion") ? ConfiguracionRabbit.RUTA_COMPRA_ANULADA
                : ConfiguracionRabbit.RUTA_COMPRA_CONFIRMADA;
        rabbit.send(ConfiguracionRabbit.EXCHANGE_VENTAS, ruta,
                new Message(ejemplo(archivo).getBytes(StandardCharsets.UTF_8)));
    }

    private void publicarMetricas(String contenido) {
        rabbit.send(ConfiguracionRabbit.EXCHANGE_CRM, ConfiguracionRabbit.RUTA_METRICAS_ACTUALIZADAS,
                new Message(contenido.getBytes(StandardCharsets.UTF_8)));
    }

    private String metricas(String idEvento, long cliente, String tipo) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idEvento", idEvento);
        datos.put("tipoEventoOrigen", tipo);
        datos.put("idCliente", cliente);
        datos.put("identificadoresOrigen", List.of("CLI-5521"));
        datos.put("frecuencia", 0);
        datos.put("valor", 0);
        datos.put("categorias", List.of());
        datos.put("calculadasEn", "2026-10-16T12:00:00Z");
        try {
            return json.writeValueAsString(datos);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void esperarVersion(long version) {
        await().atMost(Duration.ofSeconds(15)).until(() -> jdbc.queryForList(
                "SELECT version FROM comportamiento.perfil_preferencias WHERE cliente_id = ?", Long.class, CLIENTE)
                .contains(version));
    }

    private List<String> categorias() {
        return jdbc.queryForList("SELECT categoria FROM comportamiento.preferencia_categoria WHERE cliente_id = ? "
                + "ORDER BY posicion", String.class, CLIENTE);
    }

    private List<Map<String, Object>> actualizaciones() {
        return jdbc.queryForList("SELECT * FROM comportamiento.actualizacion_preferencias WHERE cliente_id = ?",
                CLIENTE);
    }

    private int mensajesEnRespaldo() {
        var propiedades = admin.getQueueProperties(ConfiguracionRabbit.COLA_PREFERENCIAS_RESPALDO);
        return propiedades == null ? -1 : ((Number) propiedades.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).intValue();
    }
}
