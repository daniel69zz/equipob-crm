package com.maxiconecta.crm.interacciones.interaccion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxiconecta.crm.interacciones.compras.ComprasCliente;
import com.maxiconecta.crm.interacciones.compras.ComprasRecientes;
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
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de las pruebas de integración de SCRUM-52 (docs/interacciones/registro-interacciones.md): PostgreSQL real,
 * la auditoría compartida de las migraciones del Gateway, el servicio de Perfil simulado (el cliente 42 existe) y
 * la identidad que reenvía el Gateway.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
abstract class IntegracionInteracciones {

    // Un solo contenedor para todas las clases: Spring reutiliza el contexto entre ellas, y un contenedor por clase
    // dejaría el contexto en caché apuntando a una base ya detenida.
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    static final long CLIENTE = 42;
    static final String ID_VENTAS = "CLI-5521";
    /** Rutas bajo el cliente (docs/seguridad/convencion-rutas-clientes.md). */
    static final String RUTA = "/api/interacciones/clientes/{clienteId}/interacciones";
    static final String RUTA_INTERACCION = RUTA + "/{id}";
    static final String AGENTE = "agente.ana";
    static final String OTRO_AGENTE = "agente.luis";
    static final String PERMISOS_AGENTE = "CLIENTE_CONSULTAR,INDICADORES_CONSULTAR,INTERACCIONES_CONSULTAR,"
            + "INTERACCIONES_REGISTRAR";
    static final String PERMISOS_ADMIN = PERMISOS_AGENTE + ",AUDITORIA_CONSULTAR";

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

    /** Compras de servicio-comportamiento (SCRUM-441); por defecto el cliente no tiene. */
    @MockBean
    ComprasCliente compras;

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
        // El cliente 42 es CLI-5521 en Marketplace y Ventas (SCRUM-53).
        when(perfiles.identificadores(anyLong())).thenAnswer(i -> i.getArgument(0, Long.class) == CLIENTE
                ? Optional.of(List.of(ID_VENTAS)) : Optional.empty());
        when(compras.recientes(anyLong(), anyList(), anyInt())).thenReturn(ComprasRecientes.ninguna());
    }

    // --- Apoyo ---

    long registrarReclamo(String usuario) throws Exception {
        return respuesta(registrar(usuario, "AGENTE_ATENCION", PERMISOS_AGENTE, cuerpo("tipo", "RECLAMO",
                "descripcion", "Llegaron 2 de los 3 productos.")).andExpect(status().isCreated())).get("id").asLong();
    }

    ResultActions historial(String consulta) throws Exception {
        return conIdentidad(get("/api/interacciones/clientes/" + CLIENTE + "/interacciones" + consulta), AGENTE,
                "AGENTE_ATENCION", PERMISOS_AGENTE);
    }

    ResultActions porTexto(String texto) throws Exception {
        return conIdentidad(get(RUTA, CLIENTE).param("texto", texto), AGENTE,
                "AGENTE_ATENCION", PERMISOS_AGENTE);
    }

    ResultActions registrar(String usuario, String rol, String permisos, Map<String, Object> cuerpo) throws Exception {
        return conIdentidad(post(RUTA, CLIENTE), usuario, rol, permisos, cuerpo);
    }

    ResultActions modificar(long id, String usuario, String rol, Map<String, Object> cuerpo) throws Exception {
        return conIdentidad(put(RUTA_INTERACCION, CLIENTE, id), usuario, rol,
                "ADMINISTRADOR_CRM".equals(rol) ? PERMISOS_ADMIN : PERMISOS_AGENTE, cuerpo);
    }

    /** Cambio de estado o de tipo (SCRUM-55). */
    ResultActions seguimiento(long id, String usuario, String rol, Map<String, Object> cuerpo) throws Exception {
        return conIdentidad(patch(RUTA_INTERACCION, CLIENTE, id), usuario, rol,
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

    /** Cuerpo con los pares clave-valor dados. */
    static Map<String, Object> cuerpo(Object... pares) {
        Map<String, Object> cuerpo = new HashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            cuerpo.put((String) pares[i], pares[i + 1]);
        }
        return cuerpo;
    }

    JsonNode respuesta(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }
}
