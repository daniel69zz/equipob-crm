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
 * SCRUM-313 · Valor acumulado calculado sobre el historial real: compras nuevas, devoluciones,
 * anulaciones y clientes sin compras (docs/compra/valor-acumulado.md).
 * <p>
 * Las compras de ejemplo son V-100234 (VENTAS, CLI-5521, 350.50) y MP-88120 (MARKETPLACE,
 * mp-user-3307, 129.90).
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ValorAcumuladoIntegracionTest {

    private static final String VENTAS = "VENTAS:CLI-5521";
    private static final String MARKETPLACE = "MARKETPLACE:mp-user-3307";
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

    // --- Criterio 1: el valor acumulado es la suma de las compras vigentes ---

    @Test
    void sumaLasComprasVigentesDeAmbosCanales() throws Exception {
        ingesta.recibir(ejemplo("compra-ventas.json"));
        ingesta.recibir(ejemplo("compra-marketplace.json"));

        mvc.perform(get(RUTA).param("identificador", VENTAS, MARKETPLACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorAcumulado.valor").value(480.40))
                .andExpect(jsonPath("$.valorAcumulado.compras").value(2))
                .andExpect(jsonPath("$.valorAcumulado.sinDatos").value(false));
    }

    @Test
    void soloSumaLasComprasDeLosIdentificadoresPedidos() throws Exception {
        ingesta.recibir(ejemplo("compra-ventas.json"));
        ingesta.recibir(ejemplo("compra-marketplace.json"));

        mvc.perform(get(RUTA).param("identificador", VENTAS))
                .andExpect(jsonPath("$.valorAcumulado.valor").value(350.50))
                .andExpect(jsonPath("$.valorAcumulado.compras").value(1));
    }

    @Test
    void unClienteSinComprasTieneValorAcumuladoCeroConSinDatos() throws Exception {
        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorAcumulado.valor").value(0))
                .andExpect(jsonPath("$.valorAcumulado.sinDatos").value(true));

        mvc.perform(get(RUTA).param("identificador", "VENTAS:CLI-NO-EXISTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorAcumulado.valor").value(0))
                .andExpect(jsonPath("$.valorAcumulado.compras").value(0));
    }

    @Test
    void unaCompraNuevaActualizaElValorAcumuladoEnLaSiguienteConsulta() throws Exception {
        ingesta.recibir(ejemplo("compra-ventas.json"));
        mvc.perform(get(RUTA).param("identificador", VENTAS, MARKETPLACE))
                .andExpect(jsonPath("$.valorAcumulado.valor").value(350.50));

        ingesta.recibir(ejemplo("compra-marketplace.json"));

        mvc.perform(get(RUTA).param("identificador", VENTAS, MARKETPLACE))
                .andExpect(jsonPath("$.valorAcumulado.valor").value(480.40))
                .andExpect(jsonPath("$.valorAcumulado.compras").value(2));
    }

    @Test
    void unaDevolucionParcialBajaElValorAcumuladoSinQuitarLaCompraDelConteo() throws Exception {
        ingesta.recibir(ejemplo("compra-ventas.json"));
        ingesta.recibir(ejemplo("compra-marketplace.json"));
        ingesta.recibirAnulacion(ejemplo("anulacion-parcial-ventas.json"));
        assertThat(eventos.findAll()).allMatch(e -> e.getEstado() == EstadoEvento.PROCESADO);

        // Ventas conserva 300.00 tras devolver 50.50: 300.00 + 129.90
        mvc.perform(get(RUTA).param("identificador", VENTAS, MARKETPLACE))
                .andExpect(jsonPath("$.valorAcumulado.valor").value(429.90))
                .andExpect(jsonPath("$.valorAcumulado.compras").value(2));
    }

    // --- Criterio 2: una compra anulada deja de sumar ---

    @Test
    void unaCompraAnuladaDejaDeSumarAlValorAcumulado() throws Exception {
        ingesta.recibir(ejemplo("compra-ventas.json"));
        ingesta.recibir(ejemplo("compra-marketplace.json"));

        mvc.perform(get(RUTA).param("identificador", VENTAS, MARKETPLACE))
                .andExpect(jsonPath("$.valorAcumulado.valor").value(480.40));

        ingesta.recibirAnulacion(ejemplo("anulacion-total-ventas.json").replace("300.00", "350.50"));
        assertThat(eventos.findAll()).allMatch(e -> e.getEstado() == EstadoEvento.PROCESADO);

        mvc.perform(get(RUTA).param("identificador", VENTAS, MARKETPLACE))
                .andExpect(jsonPath("$.valorAcumulado.valor").value(129.90))
                .andExpect(jsonPath("$.valorAcumulado.compras").value(1));
    }

    @Test
    void siTodasLasComprasSeAnulanElValorAcumuladoQuedaEnCero() throws Exception {
        ingesta.recibir(ejemplo("compra-ventas.json"));
        ingesta.recibirAnulacion(ejemplo("anulacion-total-ventas.json").replace("300.00", "350.50"));

        mvc.perform(get(RUTA).param("identificador", VENTAS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorAcumulado.valor").value(0))
                .andExpect(jsonPath("$.valorAcumulado.sinDatos").value(true))
                .andExpect(jsonPath("$.valorAcumulado.compras").value(0));
    }

    @Test
    void rechazaUnIdentificadorMalFormado() throws Exception {
        mvc.perform(get(RUTA).param("identificador", "CLI-5521"))
                .andExpect(status().isBadRequest());
    }
}
