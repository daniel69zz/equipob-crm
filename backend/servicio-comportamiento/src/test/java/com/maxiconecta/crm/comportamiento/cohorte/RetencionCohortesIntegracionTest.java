package com.maxiconecta.crm.comportamiento.cohorte;

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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-332 · Retención por cohortes (SCRUM-36, docs/compra/retencion-cohortes.md) con PostgreSQL
 * real: el cálculo manual de referencia a partir de eventos de compra y anulación, zona horaria en el
 * borde del mes, compras con fecha futura, cohortes anteriores al rango y validación de parámetros.
 * Mes en curso: octubre de 2026 (hora de La Paz).
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RetencionCohortesIntegracionTest {

    private static final String RUTA = "/api/comportamiento/cohortes";
    private static final Instant REFERENCIA = Instant.parse("2026-10-10T12:00:00Z");

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
    private EventoRecibidoRepository eventos;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE comportamiento.deteccion_cambio_comportamiento, "
                + "comportamiento.estado_comportamiento_cliente, comportamiento.cliente_nivel_consumo, "
                + "comportamiento.cliente_inactivo, comportamiento.frecuencia_compra, "
                + "comportamiento.intento_reproceso, comportamiento.evento_procesado, comportamiento.anulacion_item, "
                + "comportamiento.anulacion, comportamiento.compra_item, comportamiento.compra, "
                + "comportamiento.evento_recibido");
        when(perfiles.buscar(anyString())).thenReturn(Optional.empty());
        when(reloj.instant()).thenReturn(REFERENCIA);
        when(reloj.getZone()).thenReturn(ZoneOffset.UTC);
    }

    // --- Criterios 1, 2 y 3: el cálculo manual de referencia ---

    @Test
    void laMatrizCoincideConElCalculoManualDeReferencia() throws Exception {
        cargarCalculoManual();

        mvc.perform(get(RUTA).param("desde", "2026-07").param("hasta", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granularidad").value("MENSUAL"))
                .andExpect(jsonPath("$.zonaHoraria").value("America/La_Paz"))
                .andExpect(jsonPath("$.desde").value("2026-07"))
                .andExpect(jsonPath("$.hasta").value("2026-10"))
                .andExpect(jsonPath("$.mesActual").value("2026-10"))
                .andExpect(jsonPath("$.cohortes.length()").value(4))
                // 2026-07: A, B, C, D
                .andExpect(jsonPath("$.cohortes[0].cohorte").value("2026-07"))
                .andExpect(jsonPath("$.cohortes[0].clientes").value(4))
                .andExpect(jsonPath("$.cohortes[0].periodos.length()").value(4))
                .andExpect(jsonPath("$.cohortes[0].periodos[0].porcentaje").value(100.00))
                .andExpect(jsonPath("$.cohortes[0].periodos[1].mes").value("2026-08"))
                .andExpect(jsonPath("$.cohortes[0].periodos[1].clientes").value(2))
                .andExpect(jsonPath("$.cohortes[0].periodos[1].porcentaje").value(50.00))
                .andExpect(jsonPath("$.cohortes[0].periodos[2].clientes").value(1))
                .andExpect(jsonPath("$.cohortes[0].periodos[2].porcentaje").value(25.00))
                .andExpect(jsonPath("$.cohortes[0].periodos[3].clientes").value(1))
                .andExpect(jsonPath("$.cohortes[0].periodos[3].porcentaje").value(25.00))
                // 2026-08: E, F (septiembre anulada) y G (julio anulada)
                .andExpect(jsonPath("$.cohortes[1].cohorte").value("2026-08"))
                .andExpect(jsonPath("$.cohortes[1].clientes").value(3))
                .andExpect(jsonPath("$.cohortes[1].periodos[1].clientes").value(1))
                .andExpect(jsonPath("$.cohortes[1].periodos[1].porcentaje").value(33.33))
                .andExpect(jsonPath("$.cohortes[1].periodos[2].clientes").value(0))
                .andExpect(jsonPath("$.cohortes[1].periodos[2].porcentaje").value(0.00))
                // 2026-09: H
                .andExpect(jsonPath("$.cohortes[2].clientes").value(1))
                .andExpect(jsonPath("$.cohortes[2].periodos[1].porcentaje").value(100.00))
                // 2026-10: I
                .andExpect(jsonPath("$.cohortes[3].clientes").value(1))
                .andExpect(jsonPath("$.cohortes[3].periodos.length()").value(1));
    }

    @Test
    void unClienteCuyaUnicaCompraSeAnuloNoEntraEnNingunaCohorte() throws Exception {
        String compra = comprar("CLI-X", "2026-08-10T15:00:00Z");
        anular(compra, "CLI-X", "2026-08-12T15:00:00Z");

        mvc.perform(get(RUTA).param("desde", "2026-08").param("hasta", "2026-08"))
                .andExpect(jsonPath("$.cohortes[0].clientes").value(0))
                .andExpect(jsonPath("$.cohortes[0].periodos.length()").value(0));
    }

    // --- Datos históricos (SCRUM-328) ---

    @Test
    void losMesesSeCuentanEnLaHoraDeBoliviaYNoEnUtc() throws Exception {
        // 31 de julio a las 22:00 en La Paz (1 de agosto 02:00 UTC): es de julio.
        comprar("CLI-BORDE", "2026-08-01T02:00:00Z");

        mvc.perform(get(RUTA).param("desde", "2026-07").param("hasta", "2026-08"))
                .andExpect(jsonPath("$.cohortes[0].cohorte").value("2026-07"))
                .andExpect(jsonPath("$.cohortes[0].clientes").value(1))
                .andExpect(jsonPath("$.cohortes[1].clientes").value(0));
    }

    @Test
    void unaCompraConFechaFuturaNoCuentaComoRetencion() throws Exception {
        comprar("CLI-1", "2026-09-05T15:00:00Z");
        comprar("CLI-1", "2026-11-05T15:00:00Z");

        mvc.perform(get(RUTA).param("desde", "2026-09").param("hasta", "2026-09"))
                .andExpect(jsonPath("$.cohortes[0].clientes").value(1))
                .andExpect(jsonPath("$.cohortes[0].periodos.length()").value(2))
                .andExpect(jsonPath("$.cohortes[0].periodos[1].clientes").value(0));
    }

    @Test
    void laCohorteSeCalculaConTodoElHistorialAunqueElRangoEmpieceDespues() throws Exception {
        comprar("CLI-ANTIGUO", "2026-03-05T15:00:00Z");
        comprar("CLI-ANTIGUO", "2026-08-05T15:00:00Z");
        comprar("CLI-NUEVO", "2026-08-07T15:00:00Z");

        mvc.perform(get(RUTA).param("desde", "2026-08").param("hasta", "2026-08"))
                .andExpect(jsonPath("$.cohortes[0].clientes").value(1));
        mvc.perform(get(RUTA).param("desde", "2026-03").param("hasta", "2026-03"))
                .andExpect(jsonPath("$.cohortes[0].clientes").value(1))
                .andExpect(jsonPath("$.cohortes[0].periodos[5].mes").value("2026-08"))
                .andExpect(jsonPath("$.cohortes[0].periodos[5].clientes").value(1));
    }

    // --- Parámetros ---

    @Test
    void sinParametrosMuestraLosUltimosDoceMeses() throws Exception {
        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value("2025-11"))
                .andExpect(jsonPath("$.hasta").value("2026-10"))
                .andExpect(jsonPath("$.cohortes.length()").value(12));
    }

    @Test
    void unRangoInvalidoSeRechazaConUn400() throws Exception {
        mvc.perform(get(RUTA).param("desde", "julio")).andExpect(status().isBadRequest());
        mvc.perform(get(RUTA).param("desde", "2026-09").param("hasta", "2026-07")).andExpect(status().isBadRequest());
        mvc.perform(get(RUTA).param("hasta", "2026-11")).andExpect(status().isBadRequest());
        mvc.perform(get(RUTA).param("desde", "2023-01").param("hasta", "2026-10")).andExpect(status().isBadRequest());
    }

    // --- Utilidades ---

    /** Clientes A a I del cálculo manual de docs/compra/retencion-cohortes.md. */
    private void cargarCalculoManual() {
        comprar("A", "2026-07-03T15:00:00Z");
        comprar("A", "2026-08-03T15:00:00Z");
        comprar("A", "2026-10-03T15:00:00Z");
        comprar("B", "2026-07-04T15:00:00Z");
        comprar("B", "2026-09-04T15:00:00Z");
        comprar("C", "2026-07-05T15:00:00Z");
        comprar("D", "2026-07-06T15:00:00Z");
        comprar("D", "2026-08-06T15:00:00Z");
        comprar("D", "2026-08-20T15:00:00Z");
        comprar("E", "2026-08-07T15:00:00Z");
        comprar("E", "2026-09-07T15:00:00Z");
        comprar("F", "2026-08-08T15:00:00Z");
        anular(comprar("F", "2026-09-08T15:00:00Z"), "F", "2026-09-09T15:00:00Z");
        anular(comprar("G", "2026-07-09T15:00:00Z"), "G", "2026-07-10T15:00:00Z");
        comprar("G", "2026-08-09T15:00:00Z");
        comprar("H", "2026-09-10T15:00:00Z");
        comprar("H", "2026-10-02T15:00:00Z");
        comprar("I", "2026-10-05T15:00:00Z");
    }

    private String comprar(String idCliente, String fecha) {
        String idCompra = "V-" + UUID.randomUUID();
        ingesta.recibir("""
                {
                  "idEvento": "%s",
                  "tipoEvento": "COMPRA_CONFIRMADA",
                  "fechaEmision": "%s",
                  "compra": {
                    "idCompra": "%s",
                    "idCliente": "%s",
                    "fecha": "%s",
                    "montoTotal": 100.00,
                    "items": [ { "categoria": "Electrónica", "cantidad": 1, "monto": 100.00 } ]
                  }
                }
                """.formatted(UUID.randomUUID(), fecha, idCompra, idCliente, fecha));
        exigirProcesados();
        return idCompra;
    }

    private void anular(String idCompra, String idCliente, String fecha) {
        ingesta.recibirAnulacion("""
                {
                  "idEvento": "%s",
                  "tipoEvento": "COMPRA_ANULADA",
                  "fechaEmision": "%s",
                  "anulacion": {
                    "idAnulacion": "%s-A",
                    "idCompra": "%s",
                    "idCliente": "%s",
                    "tipo": "TOTAL",
                    "fecha": "%s",
                    "montoRevertido": 100.00,
                    "motivo": "Prueba de retención por cohortes"
                  }
                }
                """.formatted(UUID.randomUUID(), fecha, idCompra, idCompra, idCliente, fecha));
        exigirProcesados();
    }

    private void exigirProcesados() {
        assertThat(eventos.findAll()).allMatch(e -> e.getEstado() == EstadoEvento.PROCESADO);
    }
}
