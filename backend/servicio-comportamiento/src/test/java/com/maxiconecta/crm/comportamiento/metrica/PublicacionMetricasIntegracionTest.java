package com.maxiconecta.crm.comportamiento.metrica;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxiconecta.crm.comportamiento.compra.ClientePerfiles;
import com.maxiconecta.crm.comportamiento.configuracion.ConfiguracionRabbit;
import com.maxiconecta.crm.comportamiento.ingesta.EventoRecibidoRepository;
import com.maxiconecta.crm.comportamiento.preferencia.IdentificadoresPreferencias;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.maxiconecta.crm.comportamiento.ingesta.LectorEventosTest.ejemplo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * SCRUM-266 · Publicación de las métricas del cliente para la asignación automática de segmento
 * (docs/segmentacion/asignacion-automatica.md), con los eventos simulados de Marketplace y Ventas
 * publicados en RabbitMQ y PostgreSQL reales. Una cola de prueba hace de servicio-segmentacion.
 * <p>
 * V-100234 (CLI-5521): 350.50 = Electrónica 300.00 + Accesorios 50.50. MP-88120 (mp-user-3307): 129.90.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PublicacionMetricasIntegracionTest {

    private static final String COLA_PRUEBA = "prueba.segmentacion.metricas";
    private static final long CLIENTE = 42;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @MockBean
    private ClientePerfiles perfiles;

    // La actualización de preferencias (SCRUM-42) también consume estas métricas; aquí no consulta Perfil.
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

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE comportamiento.frecuencia_compra, comportamiento.intento_reproceso, "
                + "comportamiento.evento_procesado, comportamiento.anulacion_item, comportamiento.anulacion, "
                + "comportamiento.compra_item, comportamiento.compra, comportamiento.evento_recibido");
        Queue cola = new Queue(COLA_PRUEBA, false, false, false);
        admin.declareQueue(cola);
        admin.declareBinding(BindingBuilder.bind(cola).to(new TopicExchange(ConfiguracionRabbit.EXCHANGE_CRM))
                .with(ConfiguracionRabbit.RUTA_METRICAS_ACTUALIZADAS));
        admin.purgeQueue(COLA_PRUEBA, false);
        when(perfiles.buscar(anyString())).thenReturn(Optional.of(CLIENTE));
    }

    // --- Compra confirmada (SCRUM-262) ---

    @Test
    void unaCompraPublicaLasMetricasVigentesDelCliente() throws Exception {
        publicar("compra-ana.json");

        Message mensaje = recibir();
        JsonNode metricas = json.readTree(mensaje.getBody());
        assertThat(metricas.get("idEvento").asText()).isEqualTo("5b7a8c1e-3f2d-4e6a-9b1c-2d3e4f5a6b7c");
        assertThat(mensaje.getMessageProperties().getMessageId()).isEqualTo("5b7a8c1e-3f2d-4e6a-9b1c-2d3e4f5a6b7c");
        assertThat(metricas.get("tipoEventoOrigen").asText()).isEqualTo("COMPRA_CONFIRMADA");
        assertThat(metricas.get("idCliente").asLong()).isEqualTo(CLIENTE);
        assertThat(textos(metricas.get("identificadoresOrigen"))).containsExactly("CLI-5521");
        assertThat(metricas.get("frecuencia").asLong()).isEqualTo(1);
        assertThat(metricas.get("valor").decimalValue()).isEqualByComparingTo("350.50");
        assertThat(textos(metricas.get("categorias"))).containsExactly("Electrónica", "Accesorios");
        assertThat(metricas.get("calculadasEn").asText()).isNotBlank();
        // SCRUM-38: la clasificación por frecuencia viaja con las métricas para los criterios de segmentación.
        assertThat(metricas.get("clasificacionFrecuencia").asText()).isEqualTo("OCASIONAL");
        // SCRUM-279: el listado de clientes por segmento recibe el nivel y la última compra con la foto.
        assertThat(metricas.get("nivelConsumo").asText()).isEqualTo("BAJO");
        assertThat(instante(metricas.get("ultimaCompra"))).isEqualTo(Instant.parse("2026-09-27T19:28:10Z"));
    }

    @Test
    void lasMetricasSumanTodosLosIdentificadoresDelPerfil() throws Exception {
        publicar("compra-ana.json");
        recibir();
        publicar("compra-carlos.json");

        JsonNode metricas = json.readTree(recibir().getBody());
        assertThat(textos(metricas.get("identificadoresOrigen"))).containsExactly("CLI-5521", "mp-user-3307");
        assertThat(metricas.get("frecuencia").asLong()).isEqualTo(2);
        assertThat(metricas.get("valor").decimalValue()).isEqualByComparingTo("480.40");
    }

    // --- Reentrega (SCRUM-241) ---

    @Test
    void unEventoReentregadoNoVuelveAPublicarMetricas() {
        publicar("compra-ana.json");
        recibir();
        publicar("compra-ana.json");

        await().atMost(Duration.ofSeconds(15)).until(() -> eventos.count() == 2);
        assertThat(rabbit.receive(COLA_PRUEBA, 1_000)).isNull();
    }

    // --- Anulación y devolución (SCRUM-536) ---

    @Test
    void unaDevolucionYUnaAnulacionPublicanLasMetricasDescontadas() throws Exception {
        publicar("compra-ana.json");
        recibir();

        publicar("anulacion-parcial.json");
        JsonNode parcial = json.readTree(recibir().getBody());
        assertThat(parcial.get("idEvento").asText()).isEqualTo("8d2f6a4c-1b3e-4c5d-9e7f-0a1b2c3d4e5f");
        assertThat(parcial.get("tipoEventoOrigen").asText()).isEqualTo("COMPRA_ANULADA");
        assertThat(parcial.get("frecuencia").asLong()).isEqualTo(1);
        assertThat(parcial.get("valor").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(textos(parcial.get("categorias"))).containsExactly("Electrónica");
        // Una devolución parcial deja la compra vigente: sigue siendo la última.
        assertThat(parcial.get("nivelConsumo").asText()).isEqualTo("BAJO");
        assertThat(instante(parcial.get("ultimaCompra"))).isEqualTo(Instant.parse("2026-09-27T19:28:10Z"));

        publicar("anulacion-total.json");
        JsonNode total = json.readTree(recibir().getBody());
        assertThat(total.get("frecuencia").asLong()).isZero();
        assertThat(total.get("valor").decimalValue()).isEqualByComparingTo("0");
        assertThat(textos(total.get("categorias"))).isEmpty();
        assertThat(total.get("clasificacionFrecuencia").isNull()).isTrue();
        // Sin compras vigentes queda sin nivel y sin última compra.
        assertThat(total.path("nivelConsumo").asText(null)).isNull();
        assertThat(total.path("ultimaCompra").asText(null)).isNull();
    }

    // --- Sin perfil o con error: no se publica ---

    @Test
    void unaCompraSinPerfilVinculadoNoPublicaMetricas() {
        when(perfiles.buscar(anyString())).thenReturn(Optional.empty());
        publicar("compra-ana.json");

        await().atMost(Duration.ofSeconds(15)).until(() -> eventos.count() == 1);
        assertThat(rabbit.receive(COLA_PRUEBA, 1_000)).isNull();
    }

    @Test
    void unEventoFallidoNoPublicaMetricas() {
        publicar("compra-sin-cliente.json");

        await().atMost(Duration.ofSeconds(15)).until(() -> eventos.count() == 1);
        assertThat(rabbit.receive(COLA_PRUEBA, 1_000)).isNull();
    }

    private void publicar(String archivo) {
        String ruta = archivo.startsWith("anulacion") ? ConfiguracionRabbit.RUTA_COMPRA_ANULADA
                : ConfiguracionRabbit.RUTA_COMPRA_CONFIRMADA;
        rabbit.send(ConfiguracionRabbit.EXCHANGE_VENTAS, ruta,
                new Message(ejemplo(archivo).getBytes(StandardCharsets.UTF_8)));
    }

    private Message recibir() {
        Message mensaje = rabbit.receive(COLA_PRUEBA, 15_000);
        assertThat(mensaje).as("mensaje de métricas publicado").isNotNull();
        return mensaje;
    }

    private static Instant instante(JsonNode fecha) {
        return OffsetDateTime.parse(fecha.asText()).toInstant();
    }

    private static List<String> textos(JsonNode arreglo) {
        List<String> textos = new ArrayList<>();
        arreglo.forEach(nodo -> textos.add(nodo.asText()));
        return textos;
    }
}
