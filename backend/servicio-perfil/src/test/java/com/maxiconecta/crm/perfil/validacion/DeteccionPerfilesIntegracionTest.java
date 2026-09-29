package com.maxiconecta.crm.perfil.validacion;

import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import com.maxiconecta.crm.perfil.cliente.EstadoPerfil;
import com.maxiconecta.crm.perfil.sincronizacion.SincronizacionClientes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static com.maxiconecta.crm.perfil.Ejemplos.ejemplo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class DeteccionPerfilesIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @Autowired
    private ClienteRepository clientes;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SincronizacionClientes sincronizacion;

    @Test
    void detectaPersisteYConsultaUnaInconsistenciaSinEsperarEventos() throws Exception {
        Long id = guardar("Ana", "AB1234");
        mvc.perform(post("/api/perfil/clientes/{id}/validacion", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INCONSISTENTE"))
                .andExpect(jsonPath("$.motivosInconsistencia[0]").value("numeroDocumento: no coincide con el formato de NIT"));
        Cliente guardado = clientes.findById(id).orElseThrow();
        assertThat(guardado.getEstado()).isEqualTo(EstadoPerfil.INCONSISTENTE);
        assertThat(guardado.getNumeroDocumento()).isEqualTo("AB1234");
        mvc.perform(get("/api/perfil/clientes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motivosInconsistencia").value("numeroDocumento: no coincide con el formato de NIT"));
        mvc.perform(get("/api/perfil/clientes").param("estado", "INCONSISTENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientes[?(@.id == " + id + ")].estado").value("INCONSISTENTE"));
    }

    @Test
    void guardaAmbasIncidenciasYReevaluarUnPerfilCorregidoLoDejaValido() throws Exception {
        Long id = guardar(null, "AB1234");
        mvc.perform(post("/api/perfil/clientes/{id}/validacion", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("INCOMPLETO"));
        Cliente cliente = clientes.findById(id).orElseThrow();
        assertThat(cliente.getMotivosIncompleto()).contains("nombres: vacío");
        assertThat(cliente.getMotivosInconsistencia()).contains("formato de NIT");
        cliente.identificar("Ana", "Perez", "NIT", "1234567");
        clientes.save(cliente);
        mvc.perform(post("/api/perfil/clientes/{id}/validacion", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("COMPLETO"));
        cliente = clientes.findById(id).orElseThrow();
        assertThat(cliente.getMotivosIncompleto()).isNull();
        assertThat(cliente.getMotivosInconsistencia()).isNull();
    }

    @Test
    void rechazaPerfilesInexistentesOAbsorbidos() throws Exception {
        mvc.perform(post("/api/perfil/clientes/9223372036854775807/validacion"))
                .andExpect(status().isNotFound());
        Long destino = guardar("Ana", "1234567");
        Long id = guardar("Ana", "AB1234");
        Cliente absorbido = clientes.findById(id).orElseThrow();
        absorbido.consolidarEn(destino);
        clientes.save(absorbido);
        mvc.perform(post("/api/perfil/clientes/{id}/validacion", id))
                .andExpect(status().isConflict());
        assertThat(clientes.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoPerfil.COMPLETO);
    }

    @Test
    void laSincronizacionDetectaInconsistenciasYUnaActualizacionParcialNoLasBorra() {
        String origen = UUID.randomUUID().toString();
        String evento = ejemplo("cliente-ventas-alta.json")
                .replace("\"tipoDocumento\": \"CI\"", "\"tipoDocumento\": \"NIT\"")
                .replace("\"numeroDocumento\": \"4455667\"", "\"numeroDocumento\": \"ZX987654\"");
        // Cambia identificadores sin depender de los demás casos de integración.
        evento = evento.replace("CLI-5521", origen)
                .replace("7c1d2e3f-4a5b-4c6d-8e7f-9a0b1c2d3e4f", UUID.randomUUID().toString());
        sincronizacion.recibir(evento);
        Long id = jdbc.queryForObject("SELECT id_cliente FROM perfil.cliente_origen WHERE id_cliente_origen = ?",
                Long.class, origen);
        assertThat(clientes.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoPerfil.INCONSISTENTE);
        sincronizacion.recibir("""
                {"idEvento":"%s","tipoEvento":"CLIENTE_ACTUALIZADO","origen":"VENTAS",
                 "fechaEmision":"2026-10-01T10:00:00-04:00","cliente":{
                 "idCliente":"%s","contacto":{"email":"nuevo@correo.com"}}}
                """.formatted(UUID.randomUUID(), origen));
        assertThat(clientes.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoPerfil.INCONSISTENTE);
        assertThat(clientes.findById(id).orElseThrow().getEmail()).isEqualTo("nuevo@correo.com");
    }

    private Long guardar(String nombres, String documento) {
        Cliente cliente = new Cliente();
        cliente.identificar(nombres, "Perez", "NIT", documento);
        cliente.actualizarContacto("ana@correo.com", null);
        return clientes.saveAndFlush(cliente).getId();
    }
}
