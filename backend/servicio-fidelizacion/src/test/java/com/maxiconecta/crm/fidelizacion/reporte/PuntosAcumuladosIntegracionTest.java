package com.maxiconecta.crm.fidelizacion.reporte;

import com.maxiconecta.crm.fidelizacion.puntos.SaldoPuntos;
import com.maxiconecta.crm.fidelizacion.puntos.SaldoPuntosRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-476 · Puntos acumulados de la cartera para el panel de indicadores (SCRUM-59) con PostgreSQL real:
 * la suma de los saldos vigentes y los clientes con saldo; un saldo en cero no cuenta como cliente con
 * puntos.
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PuntosAcumuladosIntegracionTest {

    private static final String RUTA = "/api/fidelizacion/reportes/puntos";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private SaldoPuntosRepository saldos;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void limpiar() {
        jdbc.execute("TRUNCATE fidelizacion.saldo_puntos CASCADE");
    }

    @Test
    void sumaLosSaldosVigentesYCuentaLosClientesConSaldo() throws Exception {
        OffsetDateTime ahora = OffsetDateTime.now();
        saldos.save(new SaldoPuntos(101L, 60, "BRONCE", ahora));
        saldos.save(new SaldoPuntos(102L, 50, null, ahora));
        saldos.save(new SaldoPuntos(103L, 0, null, ahora));

        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puntos").value(110))
                .andExpect(jsonPath("$.clientesConPuntos").value(2));
    }

    @Test
    void sinSaldosDevuelveCeroYNoUnError() throws Exception {
        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puntos").value(0))
                .andExpect(jsonPath("$.clientesConPuntos").value(0));
    }
}
