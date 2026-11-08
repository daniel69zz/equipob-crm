package com.maxiconecta.crm.interacciones.interaccion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxiconecta.crm.interacciones.perfil.ClientesPerfil;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-432 · Registro, edición y consulta del historial de interacciones (SCRUM-52,
 * docs/interacciones/registro-interacciones.md) con PostgreSQL real, la auditoría compartida de las migraciones del
 * Gateway, el servicio de Perfil simulado y la identidad que reenvía el Gateway.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RegistroInteraccionesIntegracionTest {

    static final long CLIENTE = 42;
    static final String AGENTE = "agente.ana";
    static final String OTRO_AGENTE = "agente.luis";
    static final String PERMISOS_AGENTE = "CLIENTE_CONSULTAR,INTERACCIONES_CONSULTAR,INTERACCIONES_REGISTRAR";
    static final String PERMISOS_ADMIN = PERMISOS_AGENTE + ",AUDITORIA_CONSULTAR";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

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
    ClientesPerfil perfiles;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE interacciones.interaccion RESTART IDENTITY CASCADE");
        when(perfiles.existe(anyLong())).thenAnswer(i -> i.getArgument(0, Long.class) == CLIENTE);
    }

    // --- Registro ---

    @Test
    void elAgenteRegistraUnReclamoConSuResponsableYSuFecha() throws Exception {
        OffsetDateTime antes = OffsetDateTime.now(ZoneOffset.UTC);

        JsonNode creada = respuesta(registrar(AGENTE, "AGENTE_ATENCION", PERMISOS_AGENTE, cuerpo(
                "tipo", "RECLAMO", "canal", "TELEFONO", "asunto", "  Pedido incompleto  ",
                "descripcion", "  Llegaron 2 de los 3 productos de la compra.  ", "referenciaCompra", "V-100234"))
                .andExpect(status().isCreated()));

        assertThat(creada.get("id").asLong()).isPositive();
        assertThat(creada.get("clienteId").asLong()).isEqualTo(CLIENTE);
        assertThat(creada.get("tipo").asText()).isEqualTo("RECLAMO");
        assertThat(creada.get("canal").asText()).isEqualTo("TELEFONO");
        assertThat(creada.get("asunto").asText()).isEqualTo("Pedido incompleto");
        assertThat(creada.get("descripcion").asText()).isEqualTo("Llegaron 2 de los 3 productos de la compra.");
        assertThat(creada.get("referenciaCompra").asText()).isEqualTo("V-100234");
        assertThat(creada.get("registradoPor").asText()).isEqualTo(AGENTE);
        assertThat(creada.get("registradoRol").asText()).isEqualTo("AGENTE_ATENCION");
        assertThat(creada.get("version").asLong()).isEqualTo(1);
        assertThat(creada.get("modificadoPor").isNull()).isTrue();
        assertThat(OffsetDateTime.parse(creada.get("registradoEn").asText()))
                .isCloseTo(antes, within(1, ChronoUnit.MINUTES));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM interacciones.interaccion", Integer.class)).isEqualTo(1);
    }

    @Test
    void losCamposOpcionalesVaciosQuedanNulos() throws Exception {
        JsonNode creada = respuesta(registrar(AGENTE, "AGENTE_ATENCION", PERMISOS_AGENTE, cuerpo(
                "tipo", "NOTA", "canal", "", "asunto", "   ", "descripcion", "Cliente prefiere ser llamado por la tarde"))
                .andExpect(status().isCreated()));

        assertThat(creada.get("canal").isNull()).isTrue();
        assertThat(creada.get("asunto").isNull()).isTrue();
        assertThat(creada.get("referenciaCompra").isNull()).isTrue();
    }

    @Test
    void sinClienteOSinDescripcionSeRechazaPorCampo() throws Exception {
        Map<String, Object> sinCliente = cuerpo("tipo", "CONSULTA", "descripcion", "   ");
        sinCliente.remove("clienteId");
        registrar(AGENTE, "AGENTE_ATENCION", PERMISOS_AGENTE, sinCliente)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.clienteId").exists())
                .andExpect(jsonPath("$.campos.descripcion").exists());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM interacciones.interaccion", Integer.class)).isZero();
    }

    @Test
    void unTipoOCanalDesconocidoYLosLargosMaximosSeRechazan() throws Exception {
        Map<String, Object> cuerpo = cuerpo("clienteId", CLIENTE, "tipo", "QUEJA", "canal", "FAX",
                "asunto", "a".repeat(121), "descripcion", "b".repeat(4001), "referenciaCompra", "c".repeat(65));
        registrar(AGENTE, "AGENTE_ATENCION", PERMISOS_AGENTE, cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.tipo").value("debe ser CONSULTA, RECLAMO o NOTA"))
                .andExpect(jsonPath("$.campos.canal").exists())
                .andExpect(jsonPath("$.campos.asunto").exists())
                .andExpect(jsonPath("$.campos.referenciaCompra").exists());

        cuerpo = cuerpo("tipo", "CONSULTA", "descripcion", "b".repeat(4001));
        registrar(AGENTE, "AGENTE_ATENCION", PERMISOS_AGENTE, cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.descripcion").value("admite hasta 4.000 caracteres"));

        // 4.000 caracteres más los espacios de los extremos sí se admiten.
        cuerpo = cuerpo("tipo", "CONSULTA", "descripcion", "  " + "b".repeat(4000) + "  ");
        registrar(AGENTE, "AGENTE_ATENCION", PERMISOS_AGENTE, cuerpo).andExpect(status().isCreated());
    }

    @Test
    void unClienteQueNoExisteEnElCrmSeRechaza() throws Exception {
        Map<String, Object> cuerpo = cuerpo("tipo", "CONSULTA", "descripcion", "Pregunta por su saldo");
        cuerpo.put("clienteId", 999);
        registrar(AGENTE, "AGENTE_ATENCION", PERMISOS_AGENTE, cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.clienteId").value("no existe en el CRM"));
    }

    @Test
    void siPerfilNoRespondeLaInteraccionNoSeGuarda() throws Exception {
        when(perfiles.existe(anyLong())).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo comprobar el cliente"));
        registrar(AGENTE, "AGENTE_ATENCION", PERMISOS_AGENTE, cuerpo("tipo", "CONSULTA", "descripcion", "Hola"))
                .andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM interacciones.interaccion", Integer.class)).isZero();
    }

    // --- Permisos (SCRUM-435) ---

    @Test
    void sinIdentidadDelGatewayResponde401() throws Exception {
        mvc.perform(post("/api/interacciones").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(cuerpo("tipo", "NOTA", "descripcion", "x"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/interacciones/clientes/{id}", CLIENTE)).andExpect(status().isUnauthorized());
    }

    @Test
    void elGerenteConsultaPeroNoRegistraAunqueRecibaElPermisoYAntesDeValidarElCuerpo() throws Exception {
        registrar("gerente", "GERENTE_COMERCIAL", PERMISOS_AGENTE, Map.of())
                .andExpect(status().isForbidden());
        registrar(AGENTE, "AGENTE_ATENCION", "INTERACCIONES_CONSULTAR", Map.of())
                .andExpect(status().isForbidden());
        conIdentidad(get("/api/interacciones/clientes/{id}", CLIENTE), "gerente", "GERENTE_COMERCIAL",
                "INTERACCIONES_CONSULTAR").andExpect(status().isOk());
        conIdentidad(get("/api/interacciones/clientes/{id}", CLIENTE), "gerente", "GERENTE_COMERCIAL",
                "CLIENTE_CONSULTAR").andExpect(status().isForbidden());
    }

    // --- Modificación ---

    @Test
    void elAgenteModificaSuInteraccionYSubeLaVersion() throws Exception {
        long id = registrarReclamo(AGENTE);

        JsonNode modificada = respuesta(modificar(id, AGENTE, "AGENTE_ATENCION", cuerpo("tipo", "RECLAMO",
                "canal", "WHATSAPP", "asunto", "Pedido incompleto", "descripcion", "Falta el cargador del pedido.",
                "version", 1)).andExpect(status().isOk()));

        assertThat(modificada.get("canal").asText()).isEqualTo("WHATSAPP");
        assertThat(modificada.get("descripcion").asText()).isEqualTo("Falta el cargador del pedido.");
        assertThat(modificada.get("referenciaCompra").isNull()).isTrue();
        assertThat(modificada.get("version").asLong()).isEqualTo(2);
        assertThat(modificada.get("modificadoPor").asText()).isEqualTo(AGENTE);
        assertThat(modificada.get("registradoPor").asText()).isEqualTo(AGENTE);
        assertThat(modificada.get("modificadoEn").isNull()).isFalse();
    }

    @Test
    void unAgenteNoModificaLaInteraccionDeOtroPeroElAdministradorSi() throws Exception {
        long id = registrarReclamo(AGENTE);
        Map<String, Object> cambio = cuerpo("tipo", "CONSULTA", "descripcion", "Era una consulta", "version", 1);

        modificar(id, OTRO_AGENTE, "AGENTE_ATENCION", cambio)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Solo puede modificar las interacciones que usted registró"));

        conIdentidad(put("/api/interacciones/{id}", id), "admin", "ADMINISTRADOR_CRM", PERMISOS_ADMIN, cambio)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modificadoPor").value("admin"))
                .andExpect(jsonPath("$.registradoPor").value(AGENTE));
    }

    @Test
    void unaEdicionSobreUnaVersionAnteriorSeRechazaCon409() throws Exception {
        long id = registrarReclamo(AGENTE);
        modificar(id, AGENTE, "AGENTE_ATENCION", cuerpo("tipo", "RECLAMO", "descripcion", "Primer cambio",
                "version", 1)).andExpect(status().isOk());

        modificar(id, AGENTE, "AGENTE_ATENCION", cuerpo("tipo", "RECLAMO", "descripcion", "Cambio viejo",
                "version", 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campos.version").value("la interacción ya está en la versión 2"));
        assertThat(jdbc.queryForObject("SELECT descripcion FROM interacciones.interaccion", String.class))
                .isEqualTo("Primer cambio");
    }

    @Test
    void sinCambiosOUnaInteraccionQueNoExisteSeRechazan() throws Exception {
        long id = registrarReclamo(AGENTE);
        modificar(id, AGENTE, "AGENTE_ATENCION", cuerpo("tipo", "RECLAMO", "descripcion",
                "  Llegaron 2 de los 3 productos.  ", "version", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La interacción no tiene cambios para guardar"));
        modificar(9999, AGENTE, "AGENTE_ATENCION", cuerpo("tipo", "RECLAMO", "descripcion", "x", "version", 1))
                .andExpect(status().isNotFound());
        modificar(id, AGENTE, "AGENTE_ATENCION", cuerpo("tipo", "RECLAMO", "descripcion", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.version").exists());
    }

    // --- Consulta del historial ---

    @Test
    void elHistorialVaDelMasRecienteAlMasAntiguoYSoloDelCliente() throws Exception {
        long primera = registrarReclamo(AGENTE);
        long segunda = respuesta(registrar(AGENTE, "AGENTE_ATENCION", PERMISOS_AGENTE, cuerpo("tipo", "CONSULTA",
                "descripcion", "Pregunta por el envío"))).get("id").asLong();
        jdbc.update("INSERT INTO interacciones.interaccion (cliente_id, tipo, descripcion, registrado_por) "
                + "VALUES (43, 'NOTA', 'De otro cliente', 'x')");

        conIdentidad(get("/api/interacciones/clientes/{id}", CLIENTE), "gerente", "GERENTE_COMERCIAL",
                "INTERACCIONES_CONSULTAR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanio").value(20))
                .andExpect(jsonPath("$.interacciones[0].id").value(segunda))
                .andExpect(jsonPath("$.interacciones[1].id").value(primera));

        conIdentidad(get("/api/interacciones/{id}", primera), "gerente", "GERENTE_COMERCIAL",
                "INTERACCIONES_CONSULTAR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("RECLAMO"));
        conIdentidad(get("/api/interacciones/{id}", 9999), "gerente", "GERENTE_COMERCIAL",
                "INTERACCIONES_CONSULTAR").andExpect(status().isNotFound());
    }

    @Test
    void elHistorialSeFiltraPorTipoTextoYFechas() throws Exception {
        registrarReclamo(AGENTE);
        registrar(AGENTE, "AGENTE_ATENCION", PERMISOS_AGENTE, cuerpo("tipo", "CONSULTA",
                "descripcion", "Pregunta por el 100% de descuento", "referenciaCompra", "V-555"));
        long antigua = respuesta(registrar(AGENTE, "AGENTE_ATENCION", PERMISOS_AGENTE, cuerpo("tipo", "NOTA",
                "descripcion", "Nota del mes pasado"))).get("id").asLong();
        // 10/10/2026 a las 23:30 en Bolivia es el 11/10 en UTC.
        jdbc.update("UPDATE interacciones.interaccion SET registrado_en = '2026-10-11T03:30:00Z' WHERE id = ?", antigua);

        historial("?tipo=reclamo").andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.interacciones[0].tipo").value("RECLAMO"));
        historial("?texto=PRODUCTOS").andExpect(jsonPath("$.total").value(1));
        historial("?texto=v-555").andExpect(jsonPath("$.total").value(1));
        // El % se busca literal, no como comodín.
        porTexto("100%").andExpect(jsonPath("$.total").value(1));
        porTexto("%").andExpect(jsonPath("$.total").value(1));
        porTexto("_").andExpect(jsonPath("$.total").value(0));
        historial("?desde=2026-10-10&hasta=2026-10-10").andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.interacciones[0].id").value(antigua));
        historial("?hasta=2026-10-09").andExpect(jsonPath("$.total").value(0));
        historial("?desde=2026-10-11").andExpect(jsonPath("$.total").value(2));
        historial("?tamanio=1&pagina=1").andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.interacciones.length()").value(1));
    }

    @Test
    void losFiltrosInvalidosSeRechazanPorCampo() throws Exception {
        historial("?tipo=QUEJA&desde=2026-10-10&hasta=2026-10-01&tamanio=500&pagina=-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.tipo").exists())
                .andExpect(jsonPath("$.campos.desde").exists())
                .andExpect(jsonPath("$.campos.tamanio").exists())
                .andExpect(jsonPath("$.campos.pagina").exists());
        historial("?desde=10/10/2026").andExpect(status().isBadRequest());
    }

    // --- Apoyo ---

    long registrarReclamo(String usuario) throws Exception {
        return respuesta(registrar(usuario, "AGENTE_ATENCION", PERMISOS_AGENTE, cuerpo("tipo", "RECLAMO",
                "descripcion", "Llegaron 2 de los 3 productos.")).andExpect(status().isCreated())).get("id").asLong();
    }

    ResultActions historial(String consulta) throws Exception {
        return conIdentidad(get("/api/interacciones/clientes/" + CLIENTE + consulta), AGENTE, "AGENTE_ATENCION",
                PERMISOS_AGENTE);
    }

    ResultActions porTexto(String texto) throws Exception {
        return conIdentidad(get("/api/interacciones/clientes/{id}", CLIENTE).param("texto", texto), AGENTE,
                "AGENTE_ATENCION", PERMISOS_AGENTE);
    }

    ResultActions registrar(String usuario, String rol, String permisos, Map<String, Object> cuerpo) throws Exception {
        return conIdentidad(post("/api/interacciones"), usuario, rol, permisos, cuerpo);
    }

    ResultActions modificar(long id, String usuario, String rol, Map<String, Object> cuerpo) throws Exception {
        return conIdentidad(put("/api/interacciones/{id}", id), usuario, rol,
                "ADMINISTRADOR_CRM".equals(rol) ? PERMISOS_ADMIN : PERMISOS_AGENTE, cuerpo);
    }

    ResultActions conIdentidad(MockHttpServletRequestBuilder solicitud, String usuario, String rol, String permisos,
                               Map<String, Object> cuerpo) throws Exception {
        return conIdentidad(solicitud.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)),
                usuario, rol, permisos);
    }

    ResultActions conIdentidad(MockHttpServletRequestBuilder solicitud, String usuario, String rol, String permisos)
            throws Exception {
        return mvc.perform(solicitud.header("X-Usuario", usuario).header("X-Usuario-Rol", rol)
                .header("X-Usuario-Permisos", permisos));
    }

    /** Cuerpo con el cliente 42 por defecto; los pares clave-valor lo completan o lo reemplazan. */
    static Map<String, Object> cuerpo(Object... pares) {
        Map<String, Object> cuerpo = new HashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            cuerpo.put((String) pares[i], pares[i + 1]);
        }
        if (!cuerpo.containsKey("clienteId") && !cuerpo.containsKey("version")) {
            cuerpo.put("clienteId", CLIENTE);
        }
        return cuerpo;
    }

    JsonNode respuesta(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }
}
