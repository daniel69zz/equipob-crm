package com.maxiconecta.crm.comportamiento.periodo;

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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-350 · Valor total y promedio por compra de un cliente en un periodo (SCRUM-39,
 * docs/compra/consumo-por-periodo.md) con PostgreSQL real: distintos periodos, compras anuladas y
 * devueltas en parte, periodos sin compras, límites de día en la zona del negocio (America/La_Paz,
 * UTC-4), varios identificadores y rangos inválidos.
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ConsumoPeriodoIntegracionTest {

    private static final String RUTA = "/api/comportamiento/clientes/42/consumo-periodo";

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
    void preparar() {
        jdbc.execute("TRUNCATE comportamiento.cliente_inactivo, comportamiento.frecuencia_compra, "
                + "comportamiento.intento_reproceso, comportamiento.evento_procesado, "
                + "comportamiento.anulacion_item, comportamiento.anulacion, "
                + "comportamiento.compra_item, comportamiento.compra, comportamiento.evento_recibido");
        when(perfiles.buscar(anyString())).thenReturn(Optional.empty());
    }

    // --- Criterio 1: un cliente con compras en el periodo ve el valor total y el promedio por compra ---

    @Test
    void sumaSoloLasComprasDelPeriodoYCalculaElPromedioPorCompra() throws Exception {
        comprar("V-AGO", "CLI-1", "2026-08-31T10:00:00-04:00", "999.00");
        comprar("V-SEP-1", "CLI-1", "2026-09-05T10:00:00-04:00", "300.00");
        comprar("V-SEP-2", "CLI-1", "2026-09-20T15:30:00-04:00", "181.00");
        comprar("V-OCT", "CLI-1", "2026-10-02T10:00:00-04:00", "999.00");

        mvc.perform(get(RUTA).param("identificador", "CLI-1").param("desde", "2026-09-01").param("hasta", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value("2026-09-01"))
                .andExpect(jsonPath("$.hasta").value("2026-09-30"))
                .andExpect(jsonPath("$.zonaHoraria").value("America/La_Paz"))
                .andExpect(jsonPath("$.total").value(481.00))
                .andExpect(jsonPath("$.promedio").value(240.50))
                .andExpect(jsonPath("$.compras").value(2))
                .andExpect(jsonPath("$.sinDatos").value(false))
                .andExpect(jsonPath("$.mensaje").doesNotExist());
    }

    @Test
    void elMismoClienteTieneUnValorDistintoEnCadaPeriodo() throws Exception {
        comprar("V-1", "CLI-1", "2026-07-10T10:00:00-04:00", "100.00");
        comprar("V-2", "CLI-1", "2026-08-10T10:00:00-04:00", "200.00");
        comprar("V-3", "CLI-1", "2026-08-25T10:00:00-04:00", "400.00");

        mvc.perform(get(RUTA).param("identificador", "CLI-1").param("desde", "2026-07-01").param("hasta", "2026-07-31"))
                .andExpect(jsonPath("$.total").value(100.00))
                .andExpect(jsonPath("$.promedio").value(100.00))
                .andExpect(jsonPath("$.compras").value(1));
        mvc.perform(get(RUTA).param("identificador", "CLI-1").param("desde", "2026-08-01").param("hasta", "2026-08-31"))
                .andExpect(jsonPath("$.total").value(600.00))
                .andExpect(jsonPath("$.promedio").value(300.00))
                .andExpect(jsonPath("$.compras").value(2));
        mvc.perform(get(RUTA).param("identificador", "CLI-1").param("desde", "2026-07-01").param("hasta", "2026-08-31"))
                .andExpect(jsonPath("$.total").value(700.00))
                .andExpect(jsonPath("$.compras").value(3));
    }

    @Test
    void lasComprasDeTodosLosIdentificadoresDelClienteSeSuman() throws Exception {
        comprar("V-1", "CLI-1", "2026-09-05T10:00:00-04:00", "300.00");
        comprar("V-2", "mp-user-1", "2026-09-06T10:00:00-04:00", "200.00");
        comprar("V-3", "otro-cliente", "2026-09-06T10:00:00-04:00", "5000.00");

        mvc.perform(get(RUTA).param("identificador", "CLI-1", "mp-user-1").param("desde", "2026-09-01")
                        .param("hasta", "2026-09-30"))
                .andExpect(jsonPath("$.total").value(500.00))
                .andExpect(jsonPath("$.promedio").value(250.00))
                .andExpect(jsonPath("$.compras").value(2));
    }

    // --- Criterio 2: las compras anuladas del periodo no se incluyen ---

    @Test
    void unaCompraAnuladaPorCompletoNoSeIncluye() throws Exception {
        comprar("V-1", "CLI-1", "2026-09-05T10:00:00-04:00", "300.00");
        comprar("V-2", "CLI-1", "2026-09-06T10:00:00-04:00", "700.00");
        anularTotal("V-2", "CLI-1", "700.00", "2026-09-10T10:00:00-04:00");

        mvc.perform(get(RUTA).param("identificador", "CLI-1").param("desde", "2026-09-01").param("hasta", "2026-09-30"))
                .andExpect(jsonPath("$.total").value(300.00))
                .andExpect(jsonPath("$.promedio").value(300.00))
                .andExpect(jsonPath("$.compras").value(1));
    }

    @Test
    void unaCompraAnuladaDespuesDelPeriodoDejaDeSumarEnSuPeriodoOriginal() throws Exception {
        comprar("V-1", "CLI-1", "2026-09-05T10:00:00-04:00", "300.00");
        anularTotal("V-1", "CLI-1", "300.00", "2026-10-15T10:00:00-04:00");

        mvc.perform(get(RUTA).param("identificador", "CLI-1").param("desde", "2026-09-01").param("hasta", "2026-09-30"))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.sinDatos").value(true));
    }

    @Test
    void unaDevolucionParcialDescuentaLoDevueltoPeroConservaLaCompra() throws Exception {
        comprarConDosItems("V-1", "CLI-1", "2026-09-05T10:00:00-04:00", "300.00", "50.50");
        comprar("V-2", "CLI-1", "2026-09-06T10:00:00-04:00", "149.50");
        devolverAccesorios("V-1", "CLI-1", "50.50", "2026-09-10T10:00:00-04:00");

        mvc.perform(get(RUTA).param("identificador", "CLI-1").param("desde", "2026-09-01").param("hasta", "2026-09-30"))
                .andExpect(jsonPath("$.total").value(449.50))
                .andExpect(jsonPath("$.promedio").value(224.75))
                .andExpect(jsonPath("$.compras").value(2));
    }

    // --- Criterio 3: un periodo sin compras muestra cero con un mensaje, no un error ---

    @Test
    void unPeriodoSinComprasDevuelveCeroConUnMensaje() throws Exception {
        comprar("V-1", "CLI-1", "2026-09-05T10:00:00-04:00", "300.00");

        mvc.perform(get(RUTA).param("identificador", "CLI-1").param("desde", "2026-01-01").param("hasta", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.promedio").value(0))
                .andExpect(jsonPath("$.compras").value(0))
                .andExpect(jsonPath("$.sinDatos").value(true))
                .andExpect(jsonPath("$.mensaje").value("El cliente no tiene compras vigentes entre 2026-01-01 y 2026-01-31."));
    }

    @Test
    void unClienteSinIdentificadoresTampocoEsUnError() throws Exception {
        mvc.perform(get(RUTA).param("desde", "2026-09-01").param("hasta", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinDatos").value(true))
                .andExpect(jsonPath("$.total").value(0));
    }

    // --- Límites del periodo: días completos en la zona horaria del negocio ---

    @Test
    void lasComprasDeLosDiasExtremosEntranYLasDeLosDiasVecinosNo() throws Exception {
        // La Paz es UTC-4: la primera es las 00:00 del 1 de septiembre y la última las 23:59 del 30.
        comprar("V-ANTES", "CLI-1", "2026-08-31T23:59:59-04:00", "1000.00");
        comprar("V-PRIMERA", "CLI-1", "2026-09-01T00:00:00-04:00", "10.00");
        comprar("V-ULTIMA", "CLI-1", "2026-09-30T23:59:59-04:00", "20.00");
        comprar("V-DESPUES", "CLI-1", "2026-10-01T00:00:00-04:00", "1000.00");

        mvc.perform(get(RUTA).param("identificador", "CLI-1").param("desde", "2026-09-01").param("hasta", "2026-09-30"))
                .andExpect(jsonPath("$.total").value(30.00))
                .andExpect(jsonPath("$.compras").value(2));
    }

    @Test
    void unaCompraDeLaNocheSeAsignaAlDiaLocalAunqueEnUtcSeaDelDiaSiguiente() throws Exception {
        // 22:30 del 30 de septiembre en La Paz son las 02:30 UTC del 1 de octubre.
        comprar("V-NOCHE", "CLI-1", "2026-09-30T22:30:00-04:00", "80.00");

        mvc.perform(get(RUTA).param("identificador", "CLI-1").param("desde", "2026-09-30").param("hasta", "2026-09-30"))
                .andExpect(jsonPath("$.total").value(80.00));
        mvc.perform(get(RUTA).param("identificador", "CLI-1").param("desde", "2026-10-01").param("hasta", "2026-10-01"))
                .andExpect(jsonPath("$.sinDatos").value(true));
    }

    // --- Criterio 4: un rango de fechas inválido se rechaza ---

    @Test
    void unRangoInvalidoSeRechazaConUn400() throws Exception {
        mvc.perform(get(RUTA).param("identificador", "CLI-1").param("desde", "2026-09-30").param("hasta", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("no puede ser posterior")));
        mvc.perform(get(RUTA).param("identificador", "CLI-1").param("hasta", "2026-09-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("'desde'")));
        mvc.perform(get(RUTA).param("identificador", "CLI-1").param("desde", "2026-09-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("'hasta'")));
        mvc.perform(get(RUTA).param("identificador", "CLI-1").param("desde", "30/09/2026").param("hasta", "2026-09-30"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(RUTA).param("identificador", "CLI-1").param("desde", "2000-01-01").param("hasta", "2026-09-30"))
                .andExpect(status().isBadRequest());
    }

    // --- Utilidades ---

    private void comprar(String idCompra, String idCliente, String fecha, String monto) {
        ingesta.recibir(compra(idCompra, idCliente, fecha, monto,
                "{ \"categoria\": \"Electrónica\", \"cantidad\": 1, \"monto\": " + monto + " }"));
        exigirProcesados();
    }

    private void comprarConDosItems(String idCompra, String idCliente, String fecha, String montoElectronica,
                                    String montoAccesorios) {
        String total = new java.math.BigDecimal(montoElectronica).add(new java.math.BigDecimal(montoAccesorios))
                .toPlainString();
        ingesta.recibir(compra(idCompra, idCliente, fecha, total,
                "{ \"categoria\": \"Electrónica\", \"cantidad\": 1, \"monto\": " + montoElectronica + " }, "
                        + "{ \"categoria\": \"Accesorios\", \"cantidad\": 2, \"monto\": " + montoAccesorios + " }"));
        exigirProcesados();
    }

    private void anularTotal(String idCompra, String idCliente, String montoRevertido, String fecha) {
        ingesta.recibirAnulacion(anulacion(idCompra + "-A", idCompra, idCliente, "TOTAL", montoRevertido, fecha, ""));
        exigirProcesados();
    }

    private void devolverAccesorios(String idCompra, String idCliente, String monto, String fecha) {
        String items = ", \"items\": [ { \"categoria\": \"Accesorios\", \"cantidad\": 2, \"monto\": " + monto + " } ]";
        ingesta.recibirAnulacion(anulacion(idCompra + "-D", idCompra, idCliente, "PARCIAL", monto, fecha, items));
        exigirProcesados();
    }

    private void exigirProcesados() {
        assertThat(eventos.findAll()).allMatch(e -> e.getEstado() == EstadoEvento.PROCESADO);
    }

    private static String compra(String idCompra, String idCliente, String fecha, String montoTotal, String items) {
        return """
                {
                  "idEvento": "%s",
                  "tipoEvento": "COMPRA_CONFIRMADA",
                  "fechaEmision": "%s",
                  "compra": {
                    "idCompra": "%s",
                    "idCliente": "%s",
                    "fecha": "%s",
                    "montoTotal": %s,
                    "items": [ %s ]
                  }
                }
                """.formatted(UUID.randomUUID(), fecha, idCompra, idCliente, fecha, montoTotal, items);
    }

    private static String anulacion(String idAnulacion, String idCompra, String idCliente, String tipo,
                                    String montoRevertido, String fecha, String items) {
        return """
                {
                  "idEvento": "%s",
                  "tipoEvento": "COMPRA_ANULADA",
                  "fechaEmision": "%s",
                  "anulacion": {
                    "idAnulacion": "%s",
                    "idCompra": "%s",
                    "idCliente": "%s",
                    "tipo": "%s",
                    "fecha": "%s",
                    "montoRevertido": %s,
                    "motivo": "Prueba de consumo por periodo"%s
                  }
                }
                """.formatted(UUID.randomUUID(), fecha, idAnulacion, idCompra, idCliente, tipo, fecha,
                montoRevertido, items);
    }
}
