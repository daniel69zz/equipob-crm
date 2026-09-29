package com.maxiconecta.crm.comportamiento.indicador;

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

/** SCRUM-19 · Recencia calculada sobre las compras confirmadas persistidas. */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RecenciaCompraIntegracionTest {

    private static final String RUTA = "/api/comportamiento/clientes/42/indicadores";
    private static final Instant REFERENCIA = Instant.parse("2026-09-30T18:00:00Z");

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
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE comportamiento.intento_reproceso, comportamiento.evento_procesado, "
                + "comportamiento.anulacion_item, comportamiento.anulacion, "
                + "comportamiento.compra_item, comportamiento.compra, comportamiento.evento_recibido");
        when(reloj.instant()).thenReturn(REFERENCIA);
    }

    @Test
    void calculaLaRecenciaDeUnaCompraConfirmada() throws Exception {
        ingesta.recibir(compraAna("11111111-1111-4111-8111-111111111111", "V-RECENCIA-1",
                "2026-09-29T18:00:00Z"));

        mvc.perform(get(RUTA).param("identificador", "CLI-5521"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recencia.ultimaCompra").value("2026-09-29T18:00:00Z"))
                .andExpect(jsonPath("$.recencia.tiempoTranscurrido").value("PT24H"))
                .andExpect(jsonPath("$.recencia.sinDatos").value(false));
    }

    @Test
    void seleccionaLaCompraMasRecienteAunqueUnaAntiguaSeRegistreDespues() throws Exception {
        ingesta.recibir(compraAna("22222222-2222-4222-8222-222222222222", "V-RECENCIA-ANTERIOR",
                "2026-09-28T10:00:00Z"));
        ingesta.recibir(compraCarlos("33333333-3333-4333-8333-333333333333", "MP-RECENCIA-RECIENTE",
                "2026-09-30T06:00:00Z"));
        ingesta.recibir(compraAna("44444444-4444-4444-8444-444444444444", "V-RECENCIA-ANTIGUA",
                "2026-09-27T23:00:00Z"));

        mvc.perform(get(RUTA).param("identificador", "CLI-5521", "mp-user-3307"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recencia.ultimaCompra").value("2026-09-30T06:00:00Z"))
                .andExpect(jsonPath("$.recencia.tiempoTranscurrido").value("PT12H"));
    }

    @Test
    void noTomaUnaCompraMasRecienteDeOtroCliente() throws Exception {
        ingesta.recibir(compraAna("55555555-5555-4555-8555-555555555555", "V-RECENCIA-CLIENTE",
                "2026-09-28T18:00:00Z"));
        ingesta.recibir(compraAna("66666666-6666-4666-8666-666666666666", "V-RECENCIA-OTRO",
                "2026-09-30T17:00:00Z").replace("CLI-5521", "CLI-OTRO"));

        mvc.perform(get(RUTA).param("identificador", "CLI-5521"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recencia.ultimaCompra").value("2026-09-28T18:00:00Z"))
                .andExpect(jsonPath("$.recencia.tiempoTranscurrido").value("PT48H"));
    }

    @Test
    void unClienteSinComprasConfirmadasRecibeSinDatos() throws Exception {
        mvc.perform(get(RUTA).param("identificador", "CLI-SIN-COMPRAS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recencia.ultimaCompra").doesNotExist())
                .andExpect(jsonPath("$.recencia.tiempoTranscurrido").doesNotExist())
                .andExpect(jsonPath("$.recencia.sinDatos").value(true));
    }

    @Test
    void unaCompraTotalmenteAnuladaNoDefineLaRecencia() throws Exception {
        ingesta.recibir(compraAna("77777777-7777-4777-8777-777777777777", "V-RECENCIA-ANULADA",
                "2026-09-30T17:00:00Z"));
        jdbc.update("UPDATE comportamiento.compra SET estado = 'ANULADA', monto_revertido = monto_total "
                + "WHERE id_compra_origen = ?", "V-RECENCIA-ANULADA");

        mvc.perform(get(RUTA).param("identificador", "CLI-5521"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recencia.sinDatos").value(true));
    }

    private static String compraAna(String idEvento, String idCompra, String fecha) {
        return ejemplo("compra-ana.json")
                .replace("5b7a8c1e-3f2d-4e6a-9b1c-2d3e4f5a6b7c", idEvento)
                .replace("V-100234", idCompra)
                .replace("2026-09-27T15:28:10-04:00", fecha);
    }

    private static String compraCarlos(String idEvento, String idCompra, String fecha) {
        return ejemplo("compra-carlos.json")
                .replace("c3d4e5f6-7a8b-4c9d-8e1f-a2b3c4d5e6f7", idEvento)
                .replace("MP-88120", idCompra)
                .replace("2026-09-27T16:02:12-04:00", fecha);
    }
}
