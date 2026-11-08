package com.maxiconecta.crm.fidelizacion.consulta;

import com.maxiconecta.crm.fidelizacion.movimiento.BitacoraMovimientos.Estado;
import com.maxiconecta.crm.fidelizacion.movimiento.ClientesPerfil;
import com.maxiconecta.crm.fidelizacion.movimiento.IngestaMovimientos;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.Optional;

import static com.maxiconecta.crm.fidelizacion.movimiento.LectorMovimientosTest.ejemplo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-402 · Consulta del saldo, el nivel y los últimos movimientos de puntos de un cliente
 * (docs/fidelizacion/consulta-saldo-puntos.md), con PostgreSQL real, la auditoría compartida creada por
 * las migraciones del Gateway y la identidad que reenvía el Gateway. CLI-5521 es el cliente 42 del CRM.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ConsultaSaldoIntegracionTest {

    private static final long CLIENTE = 42;
    private static final String SALDO = "/api/fidelizacion/clientes/{clienteId}/saldo";
    private static final String PERMISOS_AGENTE =
            "CLIENTE_CONSULTAR,INDICADORES_CONSULTAR,PUNTOS_CONSULTAR,INTERACCIONES_CONSULTAR,INTERACCIONES_REGISTRAR";
    private static final String PERMISOS_GERENTE =
            "CLIENTE_CONSULTAR,INDICADORES_CONSULTAR,SEGMENTOS_CONSULTAR,PUNTOS_CONSULTAR,INTERACCIONES_CONSULTAR";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry registro) {
        // Las migraciones reales del Gateway son dueñas de la auditoría compartida (auditoria.evento).
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("seguridad").locations("filesystem:../api-gateway/src/main/resources/db/migration").load()
                .migrate();
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockBean
    private ClientesPerfil perfiles;

    @Autowired
    private IngestaMovimientos ingesta;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    private int auditadasAntes;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE fidelizacion.movimiento_puntos, fidelizacion.evento_movimiento, fidelizacion.saldo_puntos, "
                + "fidelizacion.cambio_nivel");
        // La auditoría es de solo inserción: se cuenta a partir de lo que ya había.
        auditadasAntes = totalAuditadas();
        when(perfiles.buscar(anyString())).thenReturn(Optional.of(CLIENTE));
    }

    // --- Criterio 1: saldo, nivel y últimos movimientos en una sola consulta ---

    @Test
    void elAgenteVeElSaldoElNivelYLosMovimientosDelMasRecienteAlMasAntiguo() throws Exception {
        compraRedencionYDevolucion();

        mvc.perform(como("agente", "AGENTE_ATENCION", PERMISOS_AGENTE, get(SALDO, CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(42))
                .andExpect(jsonPath("$.tienePuntos").value(true))
                .andExpect(jsonPath("$.mensaje").value(nullValue()))
                .andExpect(jsonPath("$.saldo").value(10))
                .andExpect(jsonPath("$.nivel").value("BRONCE"))
                .andExpect(jsonPath("$.actualizadoEn").isNotEmpty())
                .andExpect(jsonPath("$.totalMovimientos").value(3))
                .andExpect(jsonPath("$.ultimosMovimientos.length()").value(3))
                .andExpect(jsonPath("$.ultimosMovimientos[0].idTransaccion").value("V-100234-D1"))
                .andExpect(jsonPath("$.ultimosMovimientos[0].tipoMovimiento").value("REVERSA"))
                .andExpect(jsonPath("$.ultimosMovimientos[0].variacionSaldo").value(-5))
                .andExpect(jsonPath("$.ultimosMovimientos[0].saldoResultante").value(10))
                .andExpect(jsonPath("$.ultimosMovimientos[0].idTransaccionRevertida").value("V-100234"))
                .andExpect(jsonPath("$.ultimosMovimientos[1].idTransaccion").value("R-5001"))
                .andExpect(jsonPath("$.ultimosMovimientos[1].tipoMovimiento").value("REDENCION"))
                .andExpect(jsonPath("$.ultimosMovimientos[1].puntos").value(20))
                .andExpect(jsonPath("$.ultimosMovimientos[1].variacionSaldo").value(-20))
                .andExpect(jsonPath("$.ultimosMovimientos[1].saldoResultante").value(15))
                .andExpect(jsonPath("$.ultimosMovimientos[2].idTransaccion").value("V-100234"))
                .andExpect(jsonPath("$.ultimosMovimientos[2].tipoMovimiento").value("ACUMULACION"))
                .andExpect(jsonPath("$.ultimosMovimientos[2].variacionSaldo").value(35))
                .andExpect(jsonPath("$.ultimosMovimientos[2].idTransaccionRevertida").value(nullValue()));
    }

    @Test
    void elParametroMovimientosLimitaLaListaPeroElTotalCuentaTodos() throws Exception {
        compraRedencionYDevolucion();

        mvc.perform(como("agente", "AGENTE_ATENCION", PERMISOS_AGENTE, get(SALDO, CLIENTE).param("movimientos", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMovimientos").value(3))
                .andExpect(jsonPath("$.ultimosMovimientos.length()").value(1))
                .andExpect(jsonPath("$.ultimosMovimientos[0].idTransaccion").value("V-100234-D1"));
    }

    @Test
    void unaCantidadDeMovimientosFueraDeRangoResponde400() throws Exception {
        for (String movimientos : new String[] {"0", "51"}) {
            mvc.perform(como("agente", "AGENTE_ATENCION", PERMISOS_AGENTE,
                            get(SALDO, CLIENTE).param("movimientos", movimientos)))
                    .andExpect(status().isBadRequest());
        }
        assertThat(consultasAuditadas()).isZero();
    }

    // --- Criterio 2: un cliente sin movimientos ve un mensaje, no un error ---

    @Test
    void unClienteSinMovimientosRespondeConElMensajeDeSinPuntos() throws Exception {
        mvc.perform(como("agente", "AGENTE_ATENCION", PERMISOS_AGENTE, get(SALDO, 7)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(7))
                .andExpect(jsonPath("$.tienePuntos").value(false))
                .andExpect(jsonPath("$.mensaje").value("Este cliente aún no tiene puntos"))
                .andExpect(jsonPath("$.saldo").value(0))
                .andExpect(jsonPath("$.nivel").value(nullValue()))
                .andExpect(jsonPath("$.totalMovimientos").value(0))
                .andExpect(jsonPath("$.ultimosMovimientos.length()").value(0));
    }

    // --- Criterio 3: solo los roles autorizados ---

    @Test
    void elAdministradorDeCrmTambienConsultaElSaldo() throws Exception {
        compraRedencionYDevolucion();

        mvc.perform(como("admin", "ADMINISTRADOR_CRM", "PUNTOS_CONSULTAR,PUNTOS_CONFIGURAR", get(SALDO, CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(10));
    }

    @Test
    void sinLaIdentidadDelGatewayResponde401() throws Exception {
        mvc.perform(get(SALDO, CLIENTE)).andExpect(status().isUnauthorized());
        assertThat(consultasAuditadas()).isZero();
    }

    @Test
    void unAgenteSinElPermisoDePuntosRecibe403() throws Exception {
        mvc.perform(como("agente", "AGENTE_ATENCION", "CLIENTE_CONSULTAR", get(SALDO, CLIENTE)))
                .andExpect(status().isForbidden());
        assertThat(consultasAuditadas()).isZero();
    }

    @Test
    void elGerenteComercialRecibe403AunqueTengaElPermisoDePuntos() throws Exception {
        compraRedencionYDevolucion();

        mvc.perform(como("gerente", "GERENTE_COMERCIAL", PERMISOS_GERENTE, get(SALDO, CLIENTE)))
                .andExpect(status().isForbidden())
                .andExpect(r -> assertThat(r.getResolvedException()).hasMessageContaining("Administrador de CRM"));
        assertThat(consultasAuditadas()).isZero();
    }

    // --- Trazabilidad (SCRUM-401) ---

    @Test
    void cadaConsultaQuedaEnLaAuditoriaConElUsuarioElRolYLosMovimientosMostrados() throws Exception {
        compraRedencionYDevolucion();

        mvc.perform(como("agente", "AGENTE_ATENCION", PERMISOS_AGENTE, get(SALDO, CLIENTE).param("movimientos", "2")))
                .andExpect(status().isOk());

        Map<String, Object> evento = jdbc.queryForMap(
                "SELECT usuario, entidad, entidad_id, detalle FROM auditoria.evento WHERE operacion = ? "
                        + "ORDER BY id DESC LIMIT 1", AuditoriaConsultaSaldo.OPERACION);
        assertThat(consultasAuditadas()).isEqualTo(1);
        assertThat(evento.get("usuario")).isEqualTo("agente");
        assertThat(evento.get("entidad")).isEqualTo("CLIENTE");
        assertThat(evento.get("entidad_id")).isEqualTo("42");
        assertThat(evento.get("detalle").toString())
                .contains("AGENTE_ATENCION").contains("\"movimientosMostrados\":2").contains("\"totalMovimientos\":3");
    }

    /** Compra de 35 puntos, redención de 20 y devolución parcial de 5: saldo 10. */
    private void compraRedencionYDevolucion() {
        assertThat(ingesta.recibir(ejemplo("movimiento-acumulacion-ana.json"))).isEqualTo(Estado.PROCESADO);
        assertThat(ingesta.recibir(ejemplo("movimiento-redencion.json"))).isEqualTo(Estado.PROCESADO);
        assertThat(ingesta.recibir(ejemplo("anulacion-parcial.json"))).isEqualTo(Estado.PROCESADO);
    }

    private static MockHttpServletRequestBuilder como(String usuario, String rol, String permisos,
                                                      MockHttpServletRequestBuilder solicitud) {
        return solicitud.header("X-Usuario", usuario).header("X-Usuario-Rol", rol).header("X-Usuario-Permisos", permisos);
    }

    private int consultasAuditadas() {
        return totalAuditadas() - auditadasAntes;
    }

    private int totalAuditadas() {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria.evento WHERE operacion = ?", Integer.class,
                AuditoriaConsultaSaldo.OPERACION);
    }
}
