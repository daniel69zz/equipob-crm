package com.maxiconecta.crm.fidelizacion.regla;

import com.maxiconecta.crm.fidelizacion.movimiento.ClientesPerfil;
import com.maxiconecta.crm.fidelizacion.movimiento.IngestaMovimientos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-46 · La regla configurada por el Administrador de CRM se guarda como versión nueva, se aplica a
 * los movimientos siguientes y no recalcula los puntos ya otorgados (CA1), y deja su historial (CA3).
 * PostgreSQL real; los movimientos se entregan directamente a la ingesta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ReglasAcumulacionIntegracionTest {

    private static final String URL = "/api/fidelizacion/reglas/acumulacion";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private ClientesPerfil perfiles;

    @Autowired
    private IngestaMovimientos ingesta;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE fidelizacion.movimiento_puntos, fidelizacion.evento_movimiento, fidelizacion.saldo_puntos");
        jdbc.update("DELETE FROM fidelizacion.regla_acumulacion WHERE version > 1");
        when(perfiles.buscar(anyString())).thenReturn(Optional.of(42L));
    }

    @Test
    void laReglaInicialEsLaDeLaAcumulacionDePuntos() throws Exception {
        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.bolivianosPorPunto").value(10.00))
                .andExpect(jsonPath("$.montoMinimo").value(0.00));
    }

    @Test
    void unaReglaNuevaSeAplicaALosMovimientosSiguientesSinRecalcularLosAnteriores() throws Exception {
        ingesta.recibir(acumulacion("V-ANTES", "100.00"));
        assertThat(saldo()).isEqualTo(10);

        mvc.perform(administrador(put(URL)).content("""
                        {"bolivianosPorPunto": 5.00, "montoMinimo": 50.00, "motivo": "Campaña de fin de año", "version": 1}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        ingesta.recibir(acumulacion("V-DESPUES", "100.00"));
        ingesta.recibir(acumulacion("V-CHICA", "49.99"));

        assertThat(saldo()).isEqualTo(10 + 20);
        assertThat(puntos("V-ANTES")).isEqualTo(10);
        assertThat(puntos("V-DESPUES")).isEqualTo(20);
        assertThat(puntos("V-CHICA")).isZero();
        mvc.perform(get("/api/fidelizacion/clientes/42/movimientos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idTransaccion == 'V-ANTES')].versionRegla").value(1))
                .andExpect(jsonPath("$[?(@.idTransaccion == 'V-DESPUES')].versionRegla").value(2))
                .andExpect(jsonPath("$[?(@.idTransaccion == 'V-DESPUES')].montoMinimo").value(50.00));
    }

    @Test
    void elHistorialGuardaLaVersionAnteriorQuienLaCambioYCuando() throws Exception {
        mvc.perform(administrador(put(URL)).content("""
                {"bolivianosPorPunto": 8, "montoMinimo": 0, "version": 1}
                """)).andExpect(status().isOk());
        // Guardar otra vez sobre la versión 1 ya no es posible: la vigente es la 2.
        mvc.perform(administrador(put(URL)).content("""
                {"bolivianosPorPunto": 7, "montoMinimo": 0, "version": 1}
                """)).andExpect(status().isConflict());

        mvc.perform(administrador(get(URL + "/historial")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[0].bolivianosPorPunto").value(8.00))
                .andExpect(jsonPath("$[0].anteriorBolivianosPorPunto").value(10.00))
                .andExpect(jsonPath("$[0].modificadoPor").value("admin"))
                .andExpect(jsonPath("$[0].vigenteDesde").exists())
                .andExpect(jsonPath("$[1].modificadoPor").value("sistema"));
    }

    @Test
    void unaReglaInvalidaNoSeGuarda() throws Exception {
        mvc.perform(administrador(put(URL)).content("""
                {"bolivianosPorPunto": -1, "montoMinimo": 0, "version": 1}
                """)).andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM fidelizacion.regla_acumulacion", Long.class)).isEqualTo(1);
    }

    private static MockHttpServletRequestBuilder administrador(MockHttpServletRequestBuilder solicitud) {
        return solicitud.contentType(MediaType.APPLICATION_JSON).header("X-Usuario", "admin")
                .header("X-Usuario-Rol", "ADMINISTRADOR_CRM")
                .header("X-Usuario-Permisos", "PUNTOS_CONSULTAR,FIDELIZACION_CONFIGURAR");
    }

    private long saldo() {
        return jdbc.queryForObject("SELECT saldo FROM fidelizacion.saldo_puntos WHERE cliente_id = 42", Long.class);
    }

    private long puntos(String idTransaccion) {
        return jdbc.queryForObject("SELECT puntos FROM fidelizacion.movimiento_puntos WHERE id_transaccion = ?",
                Long.class, idTransaccion);
    }

    private static String acumulacion(String idTransaccion, String valor) {
        return """
                {
                  "idEvento": "%s",
                  "tipoEvento": "MOVIMIENTO_PUNTOS",
                  "fechaEmision": "2026-11-07T10:15:05-04:00",
                  "movimiento": {
                    "idTransaccion": "%s",
                    "idCliente": "CLI-5521",
                    "tipoMovimiento": "ACUMULACION",
                    "valorConsumido": %s,
                    "fecha": "2026-11-07T10:14:50-04:00"
                  }
                }
                """.formatted(UUID.randomUUID(), idTransaccion, valor);
    }
}
