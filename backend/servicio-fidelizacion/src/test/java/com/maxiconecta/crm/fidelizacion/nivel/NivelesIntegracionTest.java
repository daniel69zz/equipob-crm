package com.maxiconecta.crm.fidelizacion.nivel;

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

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-412 · Asignación de niveles con PostgreSQL real (requiere Docker): el nivel sigue al saldo (CA1),
 * los umbrales en conflicto no se guardan (CA2) y el nivel se ve en la consulta de puntos (CA3). Con la
 * regla inicial (Bs 10 por punto): Bs 4.990 = 499 puntos, Bs 10 = 1 punto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class NivelesIntegracionTest {

    private static final String URL = "/api/fidelizacion/niveles";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private ClientesPerfil perfiles;

    @Autowired
    private IngestaMovimientos ingesta;

    @Autowired
    private ServicioNiveles servicio;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE fidelizacion.movimiento_puntos, fidelizacion.evento_movimiento, fidelizacion.saldo_puntos");
        jdbc.update("DELETE FROM fidelizacion.umbral_nivel WHERE version > 1");
        jdbc.update("DELETE FROM fidelizacion.configuracion_niveles WHERE version > 1");
        jdbc.update("DELETE FROM fidelizacion.regla_acumulacion WHERE version > 1");
        when(perfiles.buscar("CLI-1")).thenReturn(Optional.of(1L));
        when(perfiles.buscar("CLI-2")).thenReturn(Optional.of(2L));
    }

    // --- Criterio 1: cuando cambia el saldo, el cliente queda en el nivel que le corresponde ---

    @Test
    void elNivelSubeConCadaAcumulacionAlCruzarElUmbral() {
        ingesta.recibir(acumulacion("V-1", "CLI-1", "4990.00", OffsetDateTime.now()));
        assertThat(nivel(1)).isEqualTo("BRONCE");
        assertThat(puntosPeriodo(1)).isEqualTo(499);

        ingesta.recibir(acumulacion("V-2", "CLI-1", "10.00", OffsetDateTime.now()));
        assertThat(nivel(1)).isEqualTo("PLATA");

        ingesta.recibir(acumulacion("V-3", "CLI-1", "15000.00", OffsetDateTime.now()));
        assertThat(nivel(1)).isEqualTo("ORO");
    }

    @Test
    void lasComprasFueraDelPeriodoDeEvaluacionNoCuentanParaElNivel() {
        ingesta.recibir(acumulacion("V-VIEJA", "CLI-1", "30000.00", OffsetDateTime.now().minusMonths(13)));
        ingesta.recibir(acumulacion("V-NUEVA", "CLI-1", "100.00", OffsetDateTime.now()));

        // SCRUM-50: la compra nueva llega con la vigencia de los 3000 puntos ya vencida; caducan antes de acumular.
        assertThat(saldo(1)).isEqualTo(10);
        assertThat(puntosPeriodo(1)).isEqualTo(10);
        assertThat(nivel(1)).isEqualTo("BRONCE");
    }

    @Test
    void cambiarLosUmbralesReclasificaATodosLosClientes() throws Exception {
        ingesta.recibir(acumulacion("V-1", "CLI-1", "6000.00", OffsetDateTime.now()));
        ingesta.recibir(acumulacion("V-2", "CLI-2", "1000.00", OffsetDateTime.now()));
        assertThat(nivel(1)).isEqualTo("PLATA");
        assertThat(nivel(2)).isEqualTo("BRONCE");

        mvc.perform(administrador(put(URL)).content("""
                        {"periodoMeses": 12, "version": 1, "motivo": "Umbrales más exigentes", "niveles": [
                          {"nivel": "BRONCE", "puntosMinimos": 100, "puntosMaximos": 999},
                          {"nivel": "PLATA", "puntosMinimos": 1000, "puntosMaximos": 4999},
                          {"nivel": "ORO", "puntosMinimos": 5000}]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuracion.version").value(2))
                .andExpect(jsonPath("$.clientesReclasificados").value(1));

        assertThat(nivel(1)).isEqualTo("BRONCE");
        assertThat(nivel(2)).isEqualTo("BRONCE");
        mvc.perform(administrador(get(URL + "/historial")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].modificadoPor").value("admin"))
                .andExpect(jsonPath("$[0].motivo").value("Umbrales más exigentes"));
    }

    @Test
    void elRecalculoBajaDeNivelAQuienYaNoTienePuntosEnElPeriodo() throws Exception {
        ingesta.recibir(acumulacion("V-1", "CLI-1", "6000.00", OffsetDateTime.now()));
        assertThat(nivel(1)).isEqualTo("PLATA");
        // Simula el paso del tiempo: la compra queda fuera del periodo de 12 meses.
        jdbc.update("UPDATE fidelizacion.movimiento_puntos SET fecha_transaccion = now() - interval '13 months'");

        mvc.perform(administrador(post(URL + "/recalcular")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientesReclasificados").value(1));
        assertThat(nivel(1)).isEqualTo("BRONCE");
        assertThat(puntosPeriodo(1)).isZero();
        assertThat(servicio.recalcularTodos()).isZero();
    }

    // --- Criterio 2: umbrales que se superponen se rechazan y no se guardan ---

    @Test
    void unosUmbralesQueSeSuperponenNoSeGuardanNiCambianLosNiveles() throws Exception {
        ingesta.recibir(acumulacion("V-1", "CLI-1", "6000.00", OffsetDateTime.now()));

        mvc.perform(administrador(put(URL)).content("""
                        {"periodoMeses": 12, "version": 1, "niveles": [
                          {"nivel": "BRONCE", "puntosMinimos": 0, "puntosMaximos": 700},
                          {"nivel": "PLATA", "puntosMinimos": 500, "puntosMaximos": 1999},
                          {"nivel": "ORO", "puntosMinimos": 2000}]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("se superponen")));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM fidelizacion.configuracion_niveles", Long.class)).isEqualTo(1);
        assertThat(nivel(1)).isEqualTo("PLATA");
    }

    // --- Criterio 3: el nivel actual se muestra en la consulta de puntos (y así en la ficha) ---

    @Test
    void laConsultaDePuntosMuestraElNivelActual() throws Exception {
        ingesta.recibir(acumulacion("V-1", "CLI-1", "25000.00", OffsetDateTime.now()));

        mvc.perform(get("/api/fidelizacion/clientes/1/puntos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(2500))
                .andExpect(jsonPath("$.nivel").value("ORO"))
                .andExpect(jsonPath("$.puntosPeriodo").value(2500))
                .andExpect(jsonPath("$.nivelActualizadoEn").exists());
    }

    private static MockHttpServletRequestBuilder administrador(MockHttpServletRequestBuilder solicitud) {
        return solicitud.contentType(MediaType.APPLICATION_JSON).header("X-Usuario", "admin")
                .header("X-Usuario-Rol", "ADMINISTRADOR_CRM")
                .header("X-Usuario-Permisos", "PUNTOS_CONSULTAR,FIDELIZACION_CONFIGURAR");
    }

    private String nivel(long cliente) {
        return jdbc.queryForObject("SELECT nivel FROM fidelizacion.saldo_puntos WHERE cliente_id = ?", String.class, cliente);
    }

    private long saldo(long cliente) {
        return jdbc.queryForObject("SELECT saldo FROM fidelizacion.saldo_puntos WHERE cliente_id = ?", Long.class, cliente);
    }

    private long puntosPeriodo(long cliente) {
        return jdbc.queryForObject("SELECT puntos_periodo FROM fidelizacion.saldo_puntos WHERE cliente_id = ?",
                Long.class, cliente);
    }

    private static String acumulacion(String idTransaccion, String idCliente, String valor, OffsetDateTime fecha) {
        return """
                {
                  "idEvento": "%s",
                  "tipoEvento": "MOVIMIENTO_PUNTOS",
                  "fechaEmision": "%s",
                  "movimiento": {
                    "idTransaccion": "%s",
                    "idCliente": "%s",
                    "tipoMovimiento": "ACUMULACION",
                    "valorConsumido": %s,
                    "fecha": "%s"
                  }
                }
                """.formatted(UUID.randomUUID(), fecha, idTransaccion, idCliente, valor, fecha);
    }
}
