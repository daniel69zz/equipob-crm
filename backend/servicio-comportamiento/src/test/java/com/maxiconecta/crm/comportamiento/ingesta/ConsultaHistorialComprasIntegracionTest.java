package com.maxiconecta.crm.comportamiento.ingesta;

import com.maxiconecta.crm.comportamiento.configuracion.ConfiguracionRabbit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static com.maxiconecta.crm.comportamiento.ingesta.LectorEventosTest.ejemplo;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-194 · Pruebas de integración de la consulta del historial de compras (SCRUM-16).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ConsultaHistorialComprasIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private EventoRecibidoRepository eventos;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void limpiarBase() {
        jdbc.execute("TRUNCATE comportamiento.intento_reproceso, comportamiento.evento_procesado, comportamiento.anulacion_item, "
                + "comportamiento.anulacion, "
                + "comportamiento.compra_item, comportamiento.compra, comportamiento.evento_recibido");
    }

    @Test
    void combinaLasComprasDeTodosLosIdentificadoresDeLaMasRecienteALaMasAntigua() throws Exception {
        publicar(ejemplo("compra-ana.json"));
        publicar(ejemplo("compra-carlos.json"));
        esperarEstado(2);

        mvc.perform(get("/api/comportamiento/clientes/CLI-INTERNO-9/compras")
                        .param("identificador", "CLI-5521", "mp-user-3307"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.content[0].referencia").value("MP-88120"))
                .andExpect(jsonPath("$.content[0].montoTotal").value(129.90))
                .andExpect(jsonPath("$.content[0].items.length()").value(2))
                .andExpect(jsonPath("$.content[1].referencia").value("V-100234"))
                .andExpect(jsonPath("$.content[1].estado").value("CONFIRMADA"));
    }

    @Test
    void soloTraeLasComprasDelIdentificadorPedidoAunqueOtroClienteTengaCompras() throws Exception {
        publicar(ejemplo("compra-ana.json"));
        publicar(ejemplo("compra-carlos.json"));
        esperarEstado(2);

        mvc.perform(get("/api/comportamiento/clientes/CLI-INTERNO-9/compras")
                        .param("identificador", "CLI-5521"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].referencia").value("V-100234"));
    }

    @Test
    void unClienteSinIdentificadoresOSinComprasRecibeUnaPaginaVaciaYNoUnError() throws Exception {
        mvc.perform(get("/api/comportamiento/clientes/CLI-SIN-COMPRAS/compras"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));

        mvc.perform(get("/api/comportamiento/clientes/CLI-SIN-COMPRAS/compras")
                        .param("identificador", "CLI-NO-EXISTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void rechazaUnIdentificadorVacioODemasiadoLargo() throws Exception {
        mvc.perform(get("/api/comportamiento/clientes/CLI-INTERNO-9/compras")
                        .param("identificador", " "))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/comportamiento/clientes/CLI-INTERNO-9/compras")
                        .param("identificador", "x".repeat(65)))
                .andExpect(status().isBadRequest());
    }

    private void publicar(String contenido) {
        rabbit.send(ConfiguracionRabbit.EXCHANGE_VENTAS, ConfiguracionRabbit.RUTA_COMPRA_CONFIRMADA,
                new org.springframework.amqp.core.Message(contenido.getBytes(StandardCharsets.UTF_8)));
    }

    private void esperarEstado(int cantidad) {
        await().atMost(Duration.ofSeconds(15)).until(eventos::findAll,
                lista -> lista.size() == cantidad && lista.stream().noneMatch(e -> e.getEstado() == EstadoEvento.RECIBIDO));
    }
}
