package com.maxiconecta.crm.fidelizacion.consulta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxiconecta.crm.fidelizacion.movimiento.BitacoraMovimientos.Estado;
import com.maxiconecta.crm.fidelizacion.movimiento.ClientesPerfil;
import com.maxiconecta.crm.fidelizacion.movimiento.ClientesPerfil.PerfilNoDisponibleException;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
 * SCRUM-548 · Consulta de puntos de Marketplace y Ventas (RIO-CRM-04,
 * docs/contratos-eventos/RIO-CRM-04-consulta-puntos.md) con PostgreSQL real, la auditoría compartida creada
 * por las migraciones del Gateway, el servicio de Perfil simulado y la identidad que el Gateway reenvía con
 * el token de servicio. CLI-5521 es el cliente 42 del CRM; su compra V-100234 de Bs 350.50 le da 35 puntos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ConsultaPuntosVentasIntegracionTest {

    private static final String RUTA = "/api/fidelizacion/ventas/clientes/{idClienteVentas}/puntos";
    private static final String VENTAS = "marketplace-ventas";
    private static final String FECHA_DEL_EJEMPLO = "2026-10-18T10:14:50-04:00";

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

    @Autowired
    private ObjectMapper json;

    private int auditadasAntes;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE fidelizacion.movimiento_puntos, fidelizacion.evento_movimiento, fidelizacion.saldo_puntos, "
                + "fidelizacion.cambio_nivel");
        // La auditoría es de solo inserción: se cuenta a partir de lo que ya había.
        auditadasAntes = totalAuditadas();
        when(perfiles.buscar(anyString())).thenReturn(Optional.empty());
        when(perfiles.buscar("CLI-5521")).thenReturn(Optional.of(42L));
        when(perfiles.buscar("CLI-7")).thenReturn(Optional.of(7L));
        when(perfiles.buscar("CLI-CAIDO")).thenThrow(new PerfilNoDisponibleException("Perfil no responde"));
    }

    // --- Criterio 1: con el token de servicio, saldo, nivel y fecha de caducidad ---

    @Test
    void ventasRecibeElSaldoDisponibleElNivelYLaFechaDeCaducidad() throws Exception {
        OffsetDateTime compra = OffsetDateTime.now(ZoneOffset.UTC).minusDays(10).truncatedTo(ChronoUnit.SECONDS);
        acumularLos35DeAnaEl(compra);
        assertThat(ingesta.recibir(ejemplo("movimiento-redencion.json"))).isEqualTo(Estado.PROCESADO);   // redime 20

        JsonNode respuesta = consultar(comoVentas(get(RUTA, "CLI-5521")));

        assertThat(respuesta.get("idClienteVentas").asText()).isEqualTo("CLI-5521");
        assertThat(respuesta.get("clienteId").asLong()).isEqualTo(42);
        assertThat(respuesta.get("tieneCuenta").asBoolean()).isTrue();
        assertThat(respuesta.get("saldoDisponible").asLong()).isEqualTo(15);
        assertThat(respuesta.get("nivel").asText()).isEqualTo("BRONCE");
        assertThat(respuesta.get("puntosCaducados").asLong()).isZero();
        assertThat(respuesta.get("vigenciaMeses").asInt()).isEqualTo(12);
        assertThat(OffsetDateTime.parse(respuesta.get("fechaCaducidad").asText()).toInstant())
                .isEqualTo(compra.plusMonths(12).toInstant());
        assertThat(respuesta.get("consultadoEn").asText()).isNotBlank();
    }

    @Test
    void laUltimaCompraConPuntosRenuevaLaVigenciaDeTodoElSaldo() throws Exception {
        OffsetDateTime primera = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(11).truncatedTo(ChronoUnit.SECONDS);
        OffsetDateTime segunda = OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).truncatedTo(ChronoUnit.SECONDS);
        acumularLos35DeAnaEl(primera);
        assertThat(ingesta.recibir(ejemplo("movimiento-acumulacion-ana.json")
                .replace("7c1e2d3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f", "7c1e2d3f-4a5b-4c6d-8e9f-0a1b2c3d4e60")
                .replace("V-100234", "V-100235")
                .replace(FECHA_DEL_EJEMPLO, segunda.toString()))).isEqualTo(Estado.PROCESADO);

        JsonNode respuesta = consultar(comoVentas(get(RUTA, "CLI-5521")));

        assertThat(respuesta.get("saldoDisponible").asLong()).isEqualTo(70);
        assertThat(OffsetDateTime.parse(respuesta.get("fechaCaducidad").asText()).toInstant())
                .isEqualTo(segunda.plusMonths(12).toInstant());
    }

    @Test
    void unSaldoConLaVigenciaVencidaNoSePuedeRedimir() throws Exception {
        OffsetDateTime compra = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(13).truncatedTo(ChronoUnit.SECONDS);
        acumularLos35DeAnaEl(compra);

        mvc.perform(comoVentas(get(RUTA, "CLI-5521")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tieneCuenta").value(true))
                .andExpect(jsonPath("$.saldoDisponible").value(0))
                .andExpect(jsonPath("$.puntosCaducados").value(35));
        // La consulta es de solo lectura: el descuento lo hace el proceso de caducidad (SCRUM-50).
        assertThat(jdbc.queryForObject("SELECT saldo FROM fidelizacion.saldo_puntos WHERE cliente_id = 42", Long.class))
                .isEqualTo(35);
    }

    // --- Criterio 3: un cliente sin cuenta de puntos responde saldo cero sin error ---

    @Test
    void unClienteDelCrmSinCuentaDePuntosRespondeSaldoCero() throws Exception {
        mvc.perform(comoVentas(get(RUTA, "CLI-7")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idClienteVentas").value("CLI-7"))
                .andExpect(jsonPath("$.clienteId").value(7))
                .andExpect(jsonPath("$.tieneCuenta").value(false))
                .andExpect(jsonPath("$.saldoDisponible").value(0))
                .andExpect(jsonPath("$.nivel").value(nullValue()))
                .andExpect(jsonPath("$.fechaCaducidad").value(nullValue()))
                .andExpect(jsonPath("$.puntosCaducados").value(0));
    }

    @Test
    void unClienteQueElCrmAunNoConoceTambienRespondeSaldoCero() throws Exception {
        mvc.perform(comoVentas(get(RUTA, "CLI-9999")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(nullValue()))
                .andExpect(jsonPath("$.tieneCuenta").value(false))
                .andExpect(jsonPath("$.saldoDisponible").value(0));
    }

    @Test
    void siPerfilNoRespondeLaConsultaDevuelve503YNoQuedaComoRespondida() throws Exception {
        mvc.perform(comoVentas(get(RUTA, "CLI-CAIDO"))).andExpect(status().isServiceUnavailable());
        assertThat(consultasAuditadas()).isZero();
    }

    // --- Criterio 2: sin la identidad del token de servicio la consulta no se procesa ---

    @Test
    void sinLaIdentidadDelGatewayResponde401() throws Exception {
        mvc.perform(get(RUTA, "CLI-5521")).andExpect(status().isUnauthorized());
        assertThat(consultasAuditadas()).isZero();
    }

    @Test
    void unUsuarioInternoConPermisoDePuntosRecibe403() throws Exception {
        mvc.perform(get(RUTA, "CLI-5521").header("X-Usuario", "admin").header("X-Usuario-Rol", "ADMINISTRADOR_CRM")
                        .header("X-Usuario-Permisos", "PUNTOS_CONSULTAR,FIDELIZACION_CONFIGURAR"))
                .andExpect(status().isForbidden());
        assertThat(consultasAuditadas()).isZero();
    }

    @Test
    void unSistemaSinElPermisoDeLaConsultaRecibe403() throws Exception {
        mvc.perform(get(RUTA, "CLI-5521").header("X-Usuario", "otro-sistema").header("X-Usuario-Rol", "SISTEMA")
                        .header("X-Usuario-Permisos", "PUNTOS_CONSULTAR"))
                .andExpect(status().isForbidden());
        assertThat(consultasAuditadas()).isZero();
    }

    // --- Criterio 4: cada consulta queda registrada en la auditoría ---

    @Test
    void cadaConsultaQuedaEnLaAuditoriaConElSistemaYElCliente() throws Exception {
        acumularLos35DeAnaEl(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));

        mvc.perform(comoVentas(get(RUTA, "CLI-5521"))).andExpect(status().isOk());
        mvc.perform(comoVentas(get(RUTA, "CLI-9999"))).andExpect(status().isOk());

        assertThat(consultasAuditadas()).isEqualTo(2);
        Map<String, Object> conCuenta = auditada("CLI-5521");
        assertThat(conCuenta.get("usuario")).isEqualTo(VENTAS);
        assertThat(conCuenta.get("entidad")).isEqualTo("CLIENTE");
        assertThat(conCuenta.get("entidad_id")).isEqualTo("42");
        assertThat(conCuenta.get("detalle").toString())
                .contains("\"tieneCuenta\":true").contains("\"puntosCaducados\":false").doesNotContain("saldo");
        Map<String, Object> desconocido = auditada("CLI-9999");
        assertThat(desconocido.get("entidad_id")).isNull();
        assertThat(desconocido.get("detalle").toString()).contains("\"tieneCuenta\":false");
    }

    private void acumularLos35DeAnaEl(OffsetDateTime fecha) {
        assertThat(ingesta.recibir(ejemplo("movimiento-acumulacion-ana.json").replace(FECHA_DEL_EJEMPLO, fecha.toString())))
                .isEqualTo(Estado.PROCESADO);
    }

    private JsonNode consultar(MockHttpServletRequestBuilder solicitud) throws Exception {
        String cuerpo = mvc.perform(solicitud).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    /** Identidad que el Gateway reenvía con el token de servicio de Marketplace y Ventas (SCRUM-545). */
    private static MockHttpServletRequestBuilder comoVentas(MockHttpServletRequestBuilder solicitud) {
        return solicitud.header("X-Usuario", VENTAS).header("X-Usuario-Rol", "SISTEMA")
                .header("X-Usuario-Permisos", "PUNTOS_CONSULTAR_VENTAS");
    }

    private Map<String, Object> auditada(String idClienteVentas) {
        return jdbc.queryForMap("SELECT usuario, entidad, entidad_id, detalle FROM auditoria.evento "
                        + "WHERE operacion = ? AND detalle LIKE ? ORDER BY id DESC LIMIT 1",
                AuditoriaConsultaSaldo.OPERACION_VENTAS, "%\"idClienteVentas\":\"" + idClienteVentas + "\"%");
    }

    private int consultasAuditadas() {
        return totalAuditadas() - auditadasAntes;
    }

    private int totalAuditadas() {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria.evento WHERE operacion = ?", Integer.class,
                AuditoriaConsultaSaldo.OPERACION_VENTAS);
    }
}
