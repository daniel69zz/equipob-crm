package com.maxiconecta.crm.comportamiento.indicador;

import com.maxiconecta.crm.comportamiento.compra.ClientePerfiles;
import com.maxiconecta.crm.comportamiento.compra.FrecuenciaCompraRepository;
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

import java.util.Optional;

import static com.maxiconecta.crm.comportamiento.ingesta.LectorEventosTest.ejemplo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SCRUM-37 · Persistencia, actualización, consulta e idempotencia de la frecuencia de compra. */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class FrecuenciaCompraIntegracionTest {

    private static final String RUTA = "/api/comportamiento/clientes/42/indicadores";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private ClientePerfiles perfiles;

    @Autowired
    private IngestaCompras ingesta;

    @Autowired
    private FrecuenciaCompraRepository frecuencias;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void limpiarBase() {
        jdbc.execute("TRUNCATE comportamiento.frecuencia_compra, comportamiento.intento_reproceso, "
                + "comportamiento.evento_procesado, comportamiento.anulacion_item, comportamiento.anulacion, "
                + "comportamiento.compra_item, comportamiento.compra, comportamiento.evento_recibido");
        when(perfiles.buscar(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void clienteSinComprasTieneFrecuenciaCero() throws Exception {
        mvc.perform(get(RUTA).param("identificador", "CLI-SIN-COMPRAS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frecuencia").value(0));

        assertThat(frecuencias.findAll()).isEmpty();
    }

    @Test
    void unaCompraSePersisteYSeRecuperaComoFrecuenciaUnoAunqueQuedePendienteDeVinculacion() throws Exception {
        ingesta.recibir(ejemplo("compra-ana.json"));

        assertThat(frecuencias.findById("CLI-5521")).get()
                .extracting("cantidad").isEqualTo(1L);
        mvc.perform(get(RUTA).param("identificador", "CLI-5521"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frecuencia").value(1));
    }

    @Test
    void cadaNuevaCompraConfirmadaActualizaElContadorPersistido() throws Exception {
        ingesta.recibir(compraAna("11111111-1111-4111-8111-111111111111", "V-FRECUENCIA-1"));
        mvc.perform(get(RUTA).param("identificador", "CLI-5521"))
                .andExpect(jsonPath("$.frecuencia").value(1));

        ingesta.recibir(compraAna("22222222-2222-4222-8222-222222222222", "V-FRECUENCIA-2"));
        ingesta.recibir(compraAna("33333333-3333-4333-8333-333333333333", "V-FRECUENCIA-3"));

        assertThat(frecuencias.findById("CLI-5521")).get()
                .extracting("cantidad").isEqualTo(3L);
        mvc.perform(get(RUTA).param("identificador", "CLI-5521"))
                .andExpect(jsonPath("$.frecuencia").value(3));
    }

    @Test
    void unEventoDuplicadoNoIncrementaDosVeces() throws Exception {
        String evento = compraAna("44444444-4444-4444-8444-444444444444", "V-FRECUENCIA-DUP");
        ingesta.recibir(evento);
        ingesta.recibir(evento);

        assertThat(frecuencias.findById("CLI-5521")).get()
                .extracting("cantidad").isEqualTo(1L);
        mvc.perform(get(RUTA).param("identificador", "CLI-5521"))
                .andExpect(jsonPath("$.frecuencia").value(1));
    }

    @Test
    void comprasDeMarketplaceYVentasCuentanIgualYSoloParaElClienteConsultado() throws Exception {
        ingesta.recibir(ejemplo("compra-ana.json"));
        ingesta.recibir(ejemplo("compra-carlos.json"));

        mvc.perform(get(RUTA).param("identificador", "CLI-5521", "mp-user-3307"))
                .andExpect(jsonPath("$.frecuencia").value(2));
        mvc.perform(get(RUTA).param("identificador", "CLI-5521"))
                .andExpect(jsonPath("$.frecuencia").value(1));
        mvc.perform(get(RUTA).param("identificador", "mp-user-3307"))
                .andExpect(jsonPath("$.frecuencia").value(1));
    }

    @Test
    void identificadoresCanonicosConElMismoSufijoNoCompartenContador() throws Exception {
        String marketplace = "MARKETPLACE:CLI-001";
        String ventas = "VENTAS:CLI-001";
        ingesta.recibir(compraAna("55555555-5555-4555-8555-555555555555", "MP-FRECUENCIA-1")
                .replace("CLI-5521", marketplace));
        ingesta.recibir(compraAna("66666666-6666-4666-8666-666666666666", "V-FRECUENCIA-1")
                .replace("CLI-5521", ventas));
        ingesta.recibir(compraAna("77777777-7777-4777-8777-777777777777", "V-FRECUENCIA-2")
                .replace("CLI-5521", ventas));

        assertThat(frecuencias.findById(marketplace)).get()
                .extracting("cantidad").isEqualTo(1L);
        assertThat(frecuencias.findById(ventas)).get()
                .extracting("cantidad").isEqualTo(2L);
        mvc.perform(get(RUTA).param("identificador", marketplace))
                .andExpect(jsonPath("$.frecuencia").value(1));
        mvc.perform(get(RUTA).param("identificador", ventas))
                .andExpect(jsonPath("$.frecuencia").value(2));
    }

    private static String compraAna(String idEvento, String idCompra) {
        return ejemplo("compra-ana.json")
                .replace("5b7a8c1e-3f2d-4e6a-9b1c-2d3e4f5a6b7c", idEvento)
                .replace("V-100234", idCompra);
    }
}
