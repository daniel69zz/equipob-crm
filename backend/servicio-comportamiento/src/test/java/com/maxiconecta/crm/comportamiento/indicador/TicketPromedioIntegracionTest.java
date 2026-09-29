package com.maxiconecta.crm.comportamiento.indicador;

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

import static com.maxiconecta.crm.comportamiento.ingesta.LectorEventosTest.ejemplo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-204 · Ticket promedio calculado sobre el historial real: compras nuevas, devoluciones,
 * anulaciones y clientes sin compras (docs/compra/ticket-promedio.md).
 * <p>
 * Las compras de ejemplo son V-100234 (cliente CLI-5521, 350.50) y MP-88120 (cliente
 * mp-user-3307, 129.90); aquí se consultan como dos identificadores del mismo cliente.
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class TicketPromedioIntegracionTest {

    private static final String ID_PRINCIPAL = "CLI-5521";
    private static final String ID_OTRA_CUENTA = "mp-user-3307";
    private static final String RUTA = "/api/comportamiento/clientes/42/indicadores";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private ClientePerfiles perfiles;

    @Autowired
    private IngestaCompras ingesta;

    @Autowired
    private EventoRecibidoRepository eventos;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void limpiarBase() {
        jdbc.execute("TRUNCATE comportamiento.intento_reproceso, comportamiento.evento_procesado, "
                + "comportamiento.anulacion_item, comportamiento.anulacion, "
                + "comportamiento.compra_item, comportamiento.compra, comportamiento.evento_recibido");
    }

    @Test
    void promediaLasComprasDeTodosLosIdentificadoresDelCliente() throws Exception {
        ingesta.recibir(ejemplo("compra-ana.json"));
        ingesta.recibir(ejemplo("compra-carlos.json"));

        mvc.perform(get(RUTA).param("identificador", ID_PRINCIPAL, ID_OTRA_CUENTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketPromedio.valor").value(240.20))
                .andExpect(jsonPath("$.ticketPromedio.compras").value(2))
                .andExpect(jsonPath("$.ticketPromedio.montoAcumulado").value(480.40))
                .andExpect(jsonPath("$.ticketPromedio.sinDatos").value(false));
    }

    @Test
    void soloCuentaLasComprasDeLosIdentificadoresPedidos() throws Exception {
        ingesta.recibir(ejemplo("compra-ana.json"));
        ingesta.recibir(ejemplo("compra-carlos.json"));

        mvc.perform(get(RUTA).param("identificador", ID_PRINCIPAL))
                .andExpect(jsonPath("$.ticketPromedio.valor").value(350.50))
                .andExpect(jsonPath("$.ticketPromedio.compras").value(1));
    }

    @Test
    void unClienteSinComprasRecibeSinDatosYNoUnError() throws Exception {
        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketPromedio.sinDatos").value(true))
                .andExpect(jsonPath("$.ticketPromedio.valor").doesNotExist());

        mvc.perform(get(RUTA).param("identificador", "CLI-NO-EXISTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketPromedio.sinDatos").value(true))
                .andExpect(jsonPath("$.ticketPromedio.compras").value(0));
    }

    @Test
    void unaCompraNuevaActualizaElTicketEnLaSiguienteConsulta() throws Exception {
        ingesta.recibir(ejemplo("compra-ana.json"));
        mvc.perform(get(RUTA).param("identificador", ID_PRINCIPAL, ID_OTRA_CUENTA))
                .andExpect(jsonPath("$.ticketPromedio.valor").value(350.50));

        ingesta.recibir(ejemplo("compra-carlos.json"));

        mvc.perform(get(RUTA).param("identificador", ID_PRINCIPAL, ID_OTRA_CUENTA))
                .andExpect(jsonPath("$.ticketPromedio.valor").value(240.20))
                .andExpect(jsonPath("$.ticketPromedio.compras").value(2));
    }

    @Test
    void unaDevolucionParcialBajaLoVigenteSinQuitarLaCompraDelConteo() throws Exception {
        ingesta.recibir(ejemplo("compra-ana.json"));
        ingesta.recibir(ejemplo("compra-carlos.json"));
        ingesta.recibirAnulacion(ejemplo("anulacion-parcial.json"));
        assertThat(eventos.findAll()).allMatch(e -> e.getEstado() == EstadoEvento.PROCESADO);

        // Ventas conserva 300.00 tras devolver 50.50: (300.00 + 129.90) / 2
        mvc.perform(get(RUTA).param("identificador", ID_PRINCIPAL, ID_OTRA_CUENTA))
                .andExpect(jsonPath("$.ticketPromedio.valor").value(214.95))
                .andExpect(jsonPath("$.ticketPromedio.compras").value(2))
                .andExpect(jsonPath("$.ticketPromedio.montoAcumulado").value(429.90));
    }

    @Test
    void unaCompraAnuladaNoCuentaNiComoCompraNiComoMonto() throws Exception {
        ingesta.recibir(ejemplo("compra-ana.json"));
        ingesta.recibir(ejemplo("compra-carlos.json"));
        ingesta.recibirAnulacion(ejemplo("anulacion-total.json").replace("300.00", "350.50"));
        assertThat(eventos.findAll()).allMatch(e -> e.getEstado() == EstadoEvento.PROCESADO);

        mvc.perform(get(RUTA).param("identificador", ID_PRINCIPAL, ID_OTRA_CUENTA))
                .andExpect(jsonPath("$.ticketPromedio.valor").value(129.90))
                .andExpect(jsonPath("$.ticketPromedio.compras").value(1));
    }

    @Test
    void siTodasLasComprasSeAnulanElClienteQuedaSinDatos() throws Exception {
        ingesta.recibir(ejemplo("compra-ana.json"));
        ingesta.recibirAnulacion(ejemplo("anulacion-total.json").replace("300.00", "350.50"));

        mvc.perform(get(RUTA).param("identificador", ID_PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketPromedio.sinDatos").value(true))
                .andExpect(jsonPath("$.ticketPromedio.compras").value(0));
    }

    @Test
    void rechazaUnIdentificadorMalFormado() throws Exception {
        mvc.perform(get(RUTA).param("identificador", " "))
                .andExpect(status().isBadRequest());
    }
}
