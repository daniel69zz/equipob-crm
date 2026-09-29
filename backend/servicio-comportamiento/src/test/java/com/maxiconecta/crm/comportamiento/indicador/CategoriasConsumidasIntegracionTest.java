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
 * SCRUM-212 · Categorías más consumidas calculadas sobre el historial real, con clientes de perfiles
 * distintos, devoluciones y anulaciones (docs/compra/categorias-mas-consumidas.md).
 * <p>
 * Las compras de ejemplo son V-100234 (VENTAS, CLI-5521: Electrónica 1 × 300.00 y Accesorios 2 × 50.50)
 * y MP-88120 (MARKETPLACE, mp-user-3307: Hogar 1 × 89.90 y Limpieza 4 × 40.00).
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CategoriasConsumidasIntegracionTest {

    private static final String VENTAS = "VENTAS:CLI-5521";
    private static final String MARKETPLACE = "MARKETPLACE:mp-user-3307";
    private static final String RUTA = "/api/comportamiento/clientes/42/categorias";

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
    void ordenaLasCategoriasDeAmbosCanalesDeMayorAMenorMonto() throws Exception {
        registrarLasDosCompras();

        mvc.perform(get(RUTA).param("identificador", VENTAS, MARKETPLACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorias.length()").value(4))
                .andExpect(jsonPath("$.categorias[0].categoria").value("Electrónica"))
                .andExpect(jsonPath("$.categorias[0].monto").value(300.00))
                .andExpect(jsonPath("$.categorias[1].categoria").value("Hogar"))
                .andExpect(jsonPath("$.categorias[2].categoria").value("Accesorios"))
                .andExpect(jsonPath("$.categorias[2].compras").value(1))
                .andExpect(jsonPath("$.categorias[2].unidades").value(2))
                .andExpect(jsonPath("$.categorias[2].monto").value(50.50))
                .andExpect(jsonPath("$.categorias[3].categoria").value("Limpieza"))
                .andExpect(jsonPath("$.categorias[3].unidades").value(4));
    }

    @Test
    void cadaClienteVeSoloLasCategoriasDeSusIdentificadores() throws Exception {
        registrarLasDosCompras();

        mvc.perform(get(RUTA).param("identificador", VENTAS))
                .andExpect(jsonPath("$.categorias.length()").value(2))
                .andExpect(jsonPath("$.categorias[0].categoria").value("Electrónica"))
                .andExpect(jsonPath("$.categorias[1].categoria").value("Accesorios"));

        mvc.perform(get(RUTA).param("identificador", MARKETPLACE))
                .andExpect(jsonPath("$.categorias.length()").value(2))
                .andExpect(jsonPath("$.categorias[0].categoria").value("Hogar"))
                .andExpect(jsonPath("$.categorias[1].categoria").value("Limpieza"));
    }

    @Test
    void unClienteSinComprasRecibeUnaListaVaciaYNoUnError() throws Exception {
        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorias.length()").value(0));

        mvc.perform(get(RUTA).param("identificador", "VENTAS:CLI-NO-EXISTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorias.length()").value(0));
    }

    @Test
    void unaCompraNuevaCambiaElRankingEnLaSiguienteConsulta() throws Exception {
        ingesta.recibir(ejemplo("compra-ventas.json"));
        mvc.perform(get(RUTA).param("identificador", VENTAS, MARKETPLACE))
                .andExpect(jsonPath("$.categorias.length()").value(2));

        ingesta.recibir(ejemplo("compra-marketplace.json"));

        mvc.perform(get(RUTA).param("identificador", VENTAS, MARKETPLACE))
                .andExpect(jsonPath("$.categorias.length()").value(4))
                .andExpect(jsonPath("$.categorias[1].categoria").value("Hogar"));
    }

    @Test
    void unaDevolucionParcialDescuentaSoloLoDevueltoDeSuCategoria() throws Exception {
        registrarLasDosCompras();
        // Se devuelve 1 de las 2 unidades de Accesorios (25.25 de los 50.50).
        ingesta.recibirAnulacion(ejemplo("anulacion-parcial-ventas.json")
                .replace("50.50", "25.25").replace("\"cantidad\": 2", "\"cantidad\": 1"));
        assertThat(eventos.findAll()).allMatch(e -> e.getEstado() == EstadoEvento.PROCESADO);

        mvc.perform(get(RUTA).param("identificador", VENTAS, MARKETPLACE))
                .andExpect(jsonPath("$.categorias.length()").value(4))
                .andExpect(jsonPath("$.categorias[2].categoria").value("Limpieza"))
                .andExpect(jsonPath("$.categorias[3].categoria").value("Accesorios"))
                .andExpect(jsonPath("$.categorias[3].unidades").value(1))
                .andExpect(jsonPath("$.categorias[3].monto").value(25.25));
    }

    @Test
    void unaCategoriaDevueltaPorCompletoSaleDelRanking() throws Exception {
        registrarLasDosCompras();
        ingesta.recibirAnulacion(ejemplo("anulacion-parcial-ventas.json"));

        mvc.perform(get(RUTA).param("identificador", VENTAS, MARKETPLACE))
                .andExpect(jsonPath("$.categorias.length()").value(3))
                .andExpect(jsonPath("$.categorias[?(@.categoria == 'Accesorios')]").isEmpty());
    }

    @Test
    void unaCompraAnuladaNoAportaNingunaCategoria() throws Exception {
        registrarLasDosCompras();
        ingesta.recibirAnulacion(ejemplo("anulacion-total-ventas.json").replace("300.00", "350.50"));
        assertThat(eventos.findAll()).allMatch(e -> e.getEstado() == EstadoEvento.PROCESADO);

        mvc.perform(get(RUTA).param("identificador", VENTAS, MARKETPLACE))
                .andExpect(jsonPath("$.categorias.length()").value(2))
                .andExpect(jsonPath("$.categorias[0].categoria").value("Hogar"))
                .andExpect(jsonPath("$.categorias[1].categoria").value("Limpieza"));

        mvc.perform(get(RUTA).param("identificador", VENTAS))
                .andExpect(jsonPath("$.categorias.length()").value(0));
    }

    @Test
    void elLimiteRecortaElRankingSinCambiarSuOrden() throws Exception {
        registrarLasDosCompras();

        mvc.perform(get(RUTA).param("identificador", VENTAS, MARKETPLACE).param("limite", "2"))
                .andExpect(jsonPath("$.categorias.length()").value(2))
                .andExpect(jsonPath("$.categorias[0].categoria").value("Electrónica"))
                .andExpect(jsonPath("$.categorias[1].categoria").value("Hogar"));
    }

    @Test
    void unaCompraSinCategoriaNoLlegaAlHistorialYQuedaFallidaEnLaBitacora() throws Exception {
        ingesta.recibir(ejemplo("compra-ventas.json").replace("Electrónica", " "));

        assertThat(eventos.findAll()).singleElement().satisfies(e -> {
            assertThat(e.getEstado()).isEqualTo(EstadoEvento.FALLIDO);
            assertThat(e.getCausa()).isNotBlank();
        });
        mvc.perform(get(RUTA).param("identificador", VENTAS))
                .andExpect(jsonPath("$.categorias.length()").value(0));
    }

    @Test
    void rechazaUnLimiteInvalido() throws Exception {
        mvc.perform(get(RUTA).param("limite", "0")).andExpect(status().isBadRequest());
    }

    private void registrarLasDosCompras() {
        ingesta.recibir(ejemplo("compra-ventas.json"));
        ingesta.recibir(ejemplo("compra-marketplace.json"));
        assertThat(eventos.findAll()).hasSize(2).allMatch(e -> e.getEstado() == EstadoEvento.PROCESADO);
    }
}
