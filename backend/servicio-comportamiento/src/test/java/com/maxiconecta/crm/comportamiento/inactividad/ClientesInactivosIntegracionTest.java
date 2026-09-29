package com.maxiconecta.crm.comportamiento.inactividad;

import com.maxiconecta.crm.comportamiento.compra.ClientePerfiles;
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

import static com.maxiconecta.crm.comportamiento.ingesta.LectorEventosTest.ejemplo;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-299 · Detección, persistencia y listado de clientes inactivos (SCRUM-31), con PostgreSQL
 * real. Sigue el mismo patrón que {@code TicketPromedioIntegracionTest}: ingesta directa
 * (sin RabbitMQ) y un reloj fijo para controlar el tiempo transcurrido.
 * <p>
 * La compra de ejemplo es V-100234 (cliente CLI-5521, 350.50).
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ClientesInactivosIntegracionTest {

    private static final String RUTA = "/api/comportamiento/clientes?estado=inactivo";
    private static final Instant REFERENCIA = Instant.parse("2026-09-29T12:00:00Z");

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
    private DetectorClientesInactivos detector;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE comportamiento.cliente_inactivo, comportamiento.frecuencia_compra, "
                + "comportamiento.intento_reproceso, comportamiento.evento_procesado, "
                + "comportamiento.anulacion_item, comportamiento.anulacion, "
                + "comportamiento.compra_item, comportamiento.compra, comportamiento.evento_recibido");
        when(reloj.instant()).thenReturn(REFERENCIA);
    }

    // --- Criterio 1: un cliente sin compras vigentes en el periodo definido aparece listado ---

    @Test
    void unClienteSinComprasHaceMasDelUmbralApareceComoInactivo() throws Exception {
        // 2026-09-29 menos 200 dias: muy por fuera del umbral por defecto (90 dias).
        ingesta.recibir(compraDe("11111111-1111-4111-8111-111111111111", "V-INACTIVO-1", "2026-03-13T10:00:00Z"));

        detector.detectar();

        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.umbralDias").value(90))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].idClienteOrigen").value("CLI-5521"))
                .andExpect(jsonPath("$.content[0].ultimaCompra").value("2026-03-13T10:00:00Z"))
                .andExpect(jsonPath("$.content[0].diasTranscurridos").value(200));
    }

    @Test
    void unClienteConUnaCompraRecienteNoApareceComoInactivo() throws Exception {
        ingesta.recibir(compraDe("22222222-2222-4222-8222-222222222222", "V-ACTIVO-1", "2026-09-25T10:00:00Z"));

        detector.detectar();

        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void unClienteSinNingunaCompraNoApareceEnElListado() throws Exception {
        detector.detectar();

        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void unaCompraTotalmenteAnuladaNoCuentaComoUltimaCompra() throws Exception {
        ingesta.recibir(compraDe("33333333-3333-4333-8333-333333333333", "V-ANULADA-1", "2026-03-13T10:00:00Z"));
        jdbc.update("UPDATE comportamiento.compra SET estado = 'ANULADA', monto_revertido = monto_total "
                + "WHERE id_compra_origen = ?", "V-ANULADA-1");

        detector.detectar();

        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    // --- Criterio 2: un cliente inactivo que vuelve a comprar deja de figurar de inmediato ---

    @Test
    void unClienteInactivoQueVuelveAComprarDejaDeFigurarSinEsperarOtraDeteccion() throws Exception {
        ingesta.recibir(compraDe("44444444-4444-4444-8444-444444444444", "V-REACTIVADO-1", "2026-03-13T10:00:00Z"));
        detector.detectar();
        mvc.perform(get(RUTA)).andExpect(jsonPath("$.total").value(1));

        ingesta.recibir(compraDe("55555555-5555-4555-8555-555555555555", "V-REACTIVADO-2", "2026-09-28T10:00:00Z"));

        // Sin volver a llamar a detectar(): la reactivación ocurre al procesar la compra.
        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    // --- Criterio 3: un usuario autorizado ve el cliente, su última compra y los días transcurridos ---

    @Test
    void elListadoMuestraElClienteLaUltimaCompraYLosDiasTranscurridos() throws Exception {
        ingesta.recibir(compraDe("66666666-6666-4666-8666-666666666666", "V-DETALLE-1", "2026-06-01T00:00:00Z"));
        detector.detectar();

        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].idClienteOrigen").value("CLI-5521"))
                .andExpect(jsonPath("$.content[0].ultimaCompra").value("2026-06-01T00:00:00Z"))
                .andExpect(jsonPath("$.content[0].diasTranscurridos").value(120));
    }

    private static String compraDe(String idEvento, String idCompra, String fecha) {
        return ejemplo("compra-ana.json")
                .replace("5b7a8c1e-3f2d-4e6a-9b1c-2d3e4f5a6b7c", idEvento)
                .replace("V-100234", idCompra)
                .replace("2026-09-27T15:28:10-04:00", fecha);
    }
}
