package com.maxiconecta.crm.fidelizacion.ajuste;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxiconecta.crm.fidelizacion.caducidad.ProcesoCaducidad;
import com.maxiconecta.crm.fidelizacion.movimiento.BitacoraMovimientos;
import com.maxiconecta.crm.fidelizacion.movimiento.BitacoraMovimientos.Estado;
import com.maxiconecta.crm.fidelizacion.movimiento.BitacoraMovimientos.EventoMovimiento;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-428 · Ajuste manual del saldo de puntos (SCRUM-51, docs/fidelizacion/ajuste-manual-puntos.md) con
 * PostgreSQL real, la auditoría compartida creada por las migraciones del Gateway, el servicio de Perfil simulado
 * y la identidad que reenvía el Gateway. CLI-5521 es el cliente 42 del CRM; una compra de Bs 350.50 le da 35
 * puntos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AjusteManualPuntosIntegracionTest {

    private static final long CLIENTE = 42;
    private static final String RUTA = "/api/fidelizacion/clientes/{clienteId}/ajustes";
    private static final String PERMISOS_ADMINISTRADOR = "FIDELIZACION_CONFIGURAR,PUNTOS_CONSULTAR";
    private static final String MOTIVO = "Compra V-100236 del 18/10 que no acumuló puntos";

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
    private BitacoraMovimientos bitacora;

    @Autowired
    private ProcesoCaducidad caducidad;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private int auditadosAntes;

    @BeforeEach
    void preparar() {
        // TRUNCATE no dispara el trigger de solo lectura de los ajustes (es por fila, para UPDATE y DELETE).
        jdbc.execute("TRUNCATE fidelizacion.ajuste_puntos, fidelizacion.movimiento_puntos, fidelizacion.evento_movimiento, "
                + "fidelizacion.saldo_puntos, fidelizacion.cambio_nivel");
        jdbc.update("DELETE FROM fidelizacion.umbral_nivel WHERE version > 1");
        jdbc.update("DELETE FROM fidelizacion.configuracion_niveles WHERE version > 1");
        // La auditoría es de solo inserción: se cuenta a partir de lo que ya había.
        auditadosAntes = totalAuditados();
        when(perfiles.buscar(anyString())).thenReturn(Optional.of(CLIENTE));
    }

    // --- Criterio 1: el saldo cambia y quedan el motivo, el responsable y la fecha ---

    @Test
    void unAjustePositivoSumaAlSaldoYRegistraElMotivoElResponsableYLaFecha() throws Exception {
        acumularLos35();
        OffsetDateTime antes = OffsetDateTime.now(ZoneOffset.UTC);

        JsonNode ajuste = respuesta(ajustar(CLIENTE, UUID.randomUUID(), 20, MOTIVO, "V-100236", 35)
                .andExpect(status().isCreated()));

        assertThat(ajuste.get("puntos").asLong()).isEqualTo(20);
        assertThat(ajuste.get("saldoAnterior").asLong()).isEqualTo(35);
        assertThat(ajuste.get("saldoResultante").asLong()).isEqualTo(55);
        assertThat(ajuste.get("usuario").asText()).isEqualTo("admin");
        assertThat(ajuste.get("rol").asText()).isEqualTo("ADMINISTRADOR_CRM");
        assertThat(ajuste.get("nivel").asText()).isEqualTo("BRONCE");
        assertThat(saldo(CLIENTE)).isEqualTo(55);

        Map<String, Object> registro = jdbc.queryForMap("SELECT * FROM fidelizacion.ajuste_puntos");
        assertThat(registro.get("motivo")).isEqualTo(MOTIVO);
        assertThat(registro.get("referencia")).isEqualTo("V-100236");
        assertThat(registro.get("usuario")).isEqualTo("admin");
        assertThat(jdbc.queryForObject("SELECT registrado_en FROM fidelizacion.ajuste_puntos", OffsetDateTime.class))
                .isCloseTo(antes, within(1, ChronoUnit.MINUTES));
    }

    @Test
    void unAjusteNegativoRestaYQuedaEnElHistorialDeMovimientos() throws Exception {
        acumularLos35();

        ajustar(CLIENTE, UUID.randomUUID(), -15, "Redención R-5001 duplicada en Ventas", null, 35)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldoResultante").value(20));

        assertThat(saldo(CLIENTE)).isEqualTo(20);
        mvc.perform(get("/api/fidelizacion/clientes/42/movimientos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipoMovimiento").value("AJUSTE"))
                .andExpect(jsonPath("$[0].puntos").value(15))
                .andExpect(jsonPath("$[0].variacionSaldo").value(-15))
                .andExpect(jsonPath("$[0].saldoResultante").value(20));
        mvc.perform(como("agente", "AGENTE_ATENCION", "PUNTOS_CONSULTAR", get("/api/fidelizacion/clientes/42/saldo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(20))
                .andExpect(jsonPath("$.ultimosMovimientos[0].tipoMovimiento").value("AJUSTE"));
    }

    @Test
    void cadaAjusteQuedaEnLaAuditoriaCompartidaConElCambioDeSaldo() throws Exception {
        acumularLos35();

        ajustar(CLIENTE, UUID.randomUUID(), 20, MOTIVO, "V-100236", 35).andExpect(status().isCreated());

        assertThat(auditados()).isEqualTo(1);
        Map<String, Object> evento = jdbc.queryForMap("SELECT usuario, entidad, entidad_id, detalle FROM auditoria.evento "
                + "WHERE operacion = ? ORDER BY id DESC LIMIT 1", AuditoriaAjustes.OPERACION);
        assertThat(evento.get("usuario")).isEqualTo("admin");
        assertThat(evento.get("entidad")).isEqualTo("CLIENTE");
        assertThat(evento.get("entidad_id")).isEqualTo("42");
        assertThat(evento.get("detalle").toString()).contains("\"puntos\":20").contains("\"saldoAnterior\":35")
                .contains("\"saldoResultante\":55").contains(MOTIVO).contains("ADMINISTRADOR_CRM");
    }

    @Test
    void unAjustePositivoAUnClienteSinCuentaDePuntosLeCreaLaCuenta() throws Exception {
        ajustar(7, UUID.randomUUID(), 50, "Bono de bienvenida acordado con el cliente", null, 0)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldoResultante").value(50));

        assertThat(saldo(7)).isEqualTo(50);
    }

    @Test
    void laMismaSolicitudConfirmadaDosVecesNoAjustaDosVeces() throws Exception {
        acumularLos35();
        UUID solicitud = UUID.randomUUID();

        ajustar(CLIENTE, solicitud, 20, MOTIVO, null, 35).andExpect(status().isCreated());
        ajustar(CLIENTE, solicitud, 20, MOTIVO, null, 55)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Este ajuste ya se aplicó"));

        assertThat(saldo(CLIENTE)).isEqualTo(55);
        assertThat(ajustes()).isEqualTo(1);
    }

    @Test
    void siElSaldoCambioMientrasSeRevisabaElAjusteSeRechaza() throws Exception {
        acumularLos35();

        ajustar(CLIENTE, UUID.randomUUID(), 20, MOTIVO, null, 30)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("ahora es 35 puntos")));

        assertThat(saldo(CLIENTE)).isEqualTo(35);
        assertThat(ajustes()).isZero();
    }

    // --- Criterio 2: un ajuste que deja el saldo negativo se rechaza ---

    @Test
    void unAjusteQueDejaElSaldoNegativoSeRechazaYNoCambiaNada() throws Exception {
        acumularLos35();

        ajustar(CLIENTE, UUID.randomUUID(), -40, "Corrección de una compra anulada", null, 35)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(
                        "El ajuste dejaría el saldo en -5: el saldo disponible es 35 y se quieren restar 40 puntos"))
                .andExpect(jsonPath("$.campos.puntos").exists());

        assertThat(saldo(CLIENTE)).isEqualTo(35);
        assertThat(ajustes()).isZero();
        assertThat(auditados()).isZero();
        assertThat(movimientos("AJUSTE")).isZero();
    }

    @Test
    void restarExactamenteElSaldoLoDejaEnCero() throws Exception {
        acumularLos35();

        ajustar(CLIENTE, UUID.randomUUID(), -35, "Puntos de una compra que era de otro cliente", null, 35)
                .andExpect(status().isCreated());

        assertThat(saldo(CLIENTE)).isZero();
    }

    @Test
    void unAjusteNegativoAUnClienteSinCuentaSeRechazaSinCrearleLaCuenta() throws Exception {
        ajustar(7, UUID.randomUUID(), -10, "Corrección de puntos inexistentes", null, 0)
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM fidelizacion.saldo_puntos WHERE cliente_id = 7", Long.class))
                .isZero();
    }

    @Test
    void lasReglasDeValidacionSeInformanPorCampo() throws Exception {
        ajustar(CLIENTE, UUID.randomUUID(), 0, MOTIVO, null, 0)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.puntos").exists());
        ajustar(CLIENTE, UUID.randomUUID(), 10, "  corto   ", null, 0)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.motivo").exists());
        ajustar(CLIENTE, UUID.randomUUID(), 100_001, MOTIVO, null, 0)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.puntos").exists());
        ajustar(CLIENTE, UUID.randomUUID(), 10, MOTIVO, "R".repeat(65), 0)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.referencia").exists());
        mvc.perform(administrador(post(RUTA, CLIENTE)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"puntos\": 10, \"motivo\": \"" + MOTIVO + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.idSolicitud").exists())
                .andExpect(jsonPath("$.campos.saldoActual").exists());
        mvc.perform(administrador(post(RUTA, CLIENTE))).andExpect(status().isBadRequest());

        assertThat(ajustes()).isZero();
    }

    // --- Criterio 3: un usuario sin rol de Administrador no puede ajustar ---

    @Test
    void unUsuarioSinRolDeAdministradorNoPuedeAjustar() throws Exception {
        acumularLos35();
        String cuerpo = cuerpo(UUID.randomUUID(), 20, MOTIVO, null, 35);

        mvc.perform(como("agente", "AGENTE_ATENCION", "PUNTOS_CONSULTAR,FIDELIZACION_CONFIGURAR", post(RUTA, CLIENTE))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isForbidden());
        mvc.perform(como("gerente", "GERENTE_COMERCIAL", "PUNTOS_CONSULTAR", post(RUTA, CLIENTE))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isForbidden());
        mvc.perform(como("admin", "ADMINISTRADOR_CRM", "PUNTOS_CONSULTAR", post(RUTA, CLIENTE))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isForbidden());
        mvc.perform(post(RUTA, CLIENTE).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized());

        assertThat(saldo(CLIENTE)).isEqualTo(35);
        assertThat(ajustes()).isZero();
    }

    @Test
    void elAccesoSeCompruebaAntesQueLaValidez() throws Exception {
        mvc.perform(como("agente", "AGENTE_ATENCION", "PUNTOS_CONSULTAR", post(RUTA, CLIENTE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"puntos\": 0}"))
                .andExpect(status().isForbidden());
    }

    // --- Criterio 4: el historial de ajustes del cliente ---

    @Test
    void elHistorialMuestraLosAjustesDelMasRecienteAlMasAntiguo() throws Exception {
        acumularLos35();
        ajustar(CLIENTE, UUID.randomUUID(), 20, MOTIVO, "V-100236", 35).andExpect(status().isCreated());
        ajustar(CLIENTE, UUID.randomUUID(), -5, "Devolución parcial que Ventas no notificó", null, 55)
                .andExpect(status().isCreated());

        mvc.perform(como("gerente", "GERENTE_COMERCIAL", "PUNTOS_CONSULTAR", get(RUTA, CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].puntos").value(-5))
                .andExpect(jsonPath("$[0].saldoAnterior").value(55))
                .andExpect(jsonPath("$[0].saldoResultante").value(50))
                .andExpect(jsonPath("$[0].motivo").value("Devolución parcial que Ventas no notificó"))
                .andExpect(jsonPath("$[0].usuario").value("admin"))
                .andExpect(jsonPath("$[0].registradoEn").exists())
                .andExpect(jsonPath("$[1].puntos").value(20))
                .andExpect(jsonPath("$[1].referencia").value("V-100236"));
        mvc.perform(como("agente", "AGENTE_ATENCION", "PUNTOS_CONSULTAR", get(RUTA, 7)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(RUTA, CLIENTE)).andExpect(status().isUnauthorized());
    }

    @Test
    void elRegistroDeAjustesEsDeSoloLectura() throws Exception {
        acumularLos35();
        ajustar(CLIENTE, UUID.randomUUID(), 20, MOTIVO, null, 35).andExpect(status().isCreated());

        assertThatThrownBy(() -> jdbc.update("UPDATE fidelizacion.ajuste_puntos SET motivo = 'cambiado después'"))
                .hasMessageContaining("Los ajustes de puntos son de solo lectura");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM fidelizacion.ajuste_puntos"))
                .hasMessageContaining("Los ajustes de puntos son de solo lectura");
    }

    // --- Caducidad, vigencia y nivel ---

    @Test
    void unSaldoVencidoCaducaAntesDelAjusteYUnAjustePositivoRenuevaLaVigencia() throws Exception {
        acumular("V-1", "350.50", OffsetDateTime.now(ZoneOffset.UTC).minusMonths(13));

        // El Administrador ve el saldo disponible en 0: los 35 puntos ya vencieron.
        ajustar(CLIENTE, UUID.randomUUID(), 20, "Compensación por un reclamo atendido", null, 0)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldoAnterior").value(0));

        assertThat(saldo(CLIENTE)).isEqualTo(20);
        assertThat(movimientos("CADUCIDAD")).isEqualTo(1);
        assertThat(caducidad.caducarVencidos().clientesConPuntosCaducados()).isZero();
        mvc.perform(como("agente", "AGENTE_ATENCION", "PUNTOS_CONSULTAR", get("/api/fidelizacion/clientes/42/saldo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(20))
                .andExpect(jsonPath("$.puntosCaducados").value(0));
    }

    @Test
    void elAjusteCuentaParaElNivelYQuedaComoOrigenDelCambio() throws Exception {
        jdbc.update("INSERT INTO fidelizacion.configuracion_niveles (version, periodo_meses, modificado_por) "
                + "VALUES (2, 12, 'prueba')");
        jdbc.update("INSERT INTO fidelizacion.umbral_nivel (version, nivel, puntos_minimos, puntos_maximos) VALUES "
                + "(2, 'BRONCE', 0, 49), (2, 'PLATA', 50, 1999), (2, 'ORO', 2000, NULL)");
        acumularLos35();

        ajustar(CLIENTE, UUID.randomUUID(), 20, MOTIVO, null, 35)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nivel").value("PLATA"));

        Map<String, Object> cambio = jdbc.queryForMap("SELECT nivel_nuevo, origen, tipo_movimiento, id_transaccion "
                + "FROM fidelizacion.cambio_nivel ORDER BY id DESC LIMIT 1");
        assertThat(cambio.get("nivel_nuevo")).isEqualTo("PLATA");
        assertThat(cambio.get("tipo_movimiento")).isEqualTo("AJUSTE");
        assertThat(cambio.get("id_transaccion").toString()).startsWith("AJUSTE-");
    }

    @Test
    void unEventoDeVentasNoPuedeTraerUnAjuste() {
        String evento = """
                {"idEvento": "%s", "tipoEvento": "MOVIMIENTO_PUNTOS", "fechaEmision": "2026-10-18T10:00:00-04:00",
                 "movimiento": {"idTransaccion": "AJUSTE-X", "idCliente": "CLI-5521", "tipoMovimiento": "AJUSTE",
                                "puntos": 10, "fecha": "2026-10-18T10:00:00-04:00"}}
                """.formatted(UUID.randomUUID());

        assertThat(ingesta.recibir(evento)).isEqualTo(Estado.FALLIDO);
        assertThat(bitacora.listar(null, 10).stream().filter(e -> "AJUSTE-X".equals(e.idTransaccion()))
                .map(EventoMovimiento::causa).findFirst().orElse(""))
                .contains("movimiento.tipoMovimiento debe ser ACUMULACION, REDENCION o REVERSA");
    }

    // --- Ayudas ---

    private ResultActions ajustar(long clienteId, UUID solicitud, long puntos, String motivo, String referencia,
                                  long saldoActual) throws Exception {
        return mvc.perform(administrador(post(RUTA, clienteId)).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(solicitud, puntos, motivo, referencia, saldoActual)));
    }

    private String cuerpo(UUID solicitud, long puntos, String motivo, String referencia, long saldoActual)
            throws Exception {
        Map<String, Object> cuerpo = new java.util.LinkedHashMap<>();
        cuerpo.put("idSolicitud", solicitud);
        cuerpo.put("puntos", puntos);
        cuerpo.put("motivo", motivo);
        cuerpo.put("referencia", referencia);
        cuerpo.put("saldoActual", saldoActual);
        return json.writeValueAsString(cuerpo);
    }

    private JsonNode respuesta(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private void acumularLos35() {
        acumular("V-100234", "350.50", OffsetDateTime.now(ZoneOffset.UTC).minusDays(5));
    }

    private void acumular(String idTransaccion, String valor, OffsetDateTime fecha) {
        OffsetDateTime enSegundos = fecha.truncatedTo(ChronoUnit.SECONDS);
        assertThat(ingesta.recibir("""
                {"idEvento": "%s", "tipoEvento": "MOVIMIENTO_PUNTOS", "fechaEmision": "%s",
                 "movimiento": {"idTransaccion": "%s", "idCliente": "CLI-5521", "tipoMovimiento": "ACUMULACION",
                                "valorConsumido": %s, "fecha": "%s"}}
                """.formatted(UUID.randomUUID(), enSegundos, idTransaccion, valor, enSegundos)))
                .isEqualTo(Estado.PROCESADO);
    }

    private long saldo(long clienteId) {
        return jdbc.queryForObject("SELECT saldo FROM fidelizacion.saldo_puntos WHERE cliente_id = ?", Long.class, clienteId);
    }

    private long ajustes() {
        return jdbc.queryForObject("SELECT count(*) FROM fidelizacion.ajuste_puntos", Long.class);
    }

    private long movimientos(String tipo) {
        return jdbc.queryForObject("SELECT count(*) FROM fidelizacion.movimiento_puntos WHERE tipo_movimiento = ?",
                Long.class, tipo);
    }

    private int auditados() {
        return totalAuditados() - auditadosAntes;
    }

    private int totalAuditados() {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria.evento WHERE operacion = ?", Integer.class,
                AuditoriaAjustes.OPERACION);
    }

    private static MockHttpServletRequestBuilder administrador(MockHttpServletRequestBuilder solicitud) {
        return como("admin", "ADMINISTRADOR_CRM", PERMISOS_ADMINISTRADOR, solicitud);
    }

    private static MockHttpServletRequestBuilder como(String usuario, String rol, String permisos,
                                                      MockHttpServletRequestBuilder solicitud) {
        return solicitud.header("X-Usuario", usuario).header("X-Usuario-Rol", rol).header("X-Usuario-Permisos", permisos);
    }
}
