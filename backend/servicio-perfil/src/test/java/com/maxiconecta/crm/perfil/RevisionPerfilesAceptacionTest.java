package com.maxiconecta.crm.perfil;

import com.maxiconecta.crm.perfil.cliente.CambioPerfil;
import com.maxiconecta.crm.perfil.cliente.CambioPerfilRepository;
import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import com.maxiconecta.crm.perfil.cliente.Direccion;
import com.maxiconecta.crm.perfil.cliente.EstadoPerfil;
import com.maxiconecta.crm.perfil.cliente.Origen;
import com.maxiconecta.crm.perfil.cliente.TipoDireccion;
import com.maxiconecta.crm.perfil.validacion.DeteccionPerfiles;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-555 · Aceptación de SCRUM-12 con perfiles de ejemplo: detección de perfiles incompletos
 * o inconsistentes, listado de revisión con filtros, y auditoría de la corrección.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RevisionPerfilesAceptacionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ClienteRepository clientes;

    @Autowired
    private CambioPerfilRepository cambiosPerfil;

    @Autowired
    private DeteccionPerfiles deteccion;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiarBase() {
        jdbc.execute("SET session_replication_role = replica; "
                + "TRUNCATE perfil.cambio_perfil, perfil.direccion, perfil.cliente_origen, perfil.cliente "
                + "RESTART IDENTITY; SET session_replication_role = DEFAULT");
    }

    // --- Criterio 1: un perfil que incumple una regla queda etiquetado con la regla incumplida ---

    @Test
    void unPerfilIncompletoQuedaEtiquetadoConLaRegla() {
        Cliente incompleto = clientes.save(ejemploIncompleto());

        deteccion.detectar(incompleto.getId());

        Cliente actualizado = clientes.findById(incompleto.getId()).orElseThrow();
        assertThat(actualizado.getEstado()).isEqualTo(EstadoPerfil.INCOMPLETO);
        assertThat(actualizado.getMotivosIncidencia()).isEqualTo("apellidos: vacío");
    }

    @Test
    void unPerfilInconsistenteQuedaEtiquetadoConLaRegla() {
        Cliente inconsistente = clientes.save(ejemploInconsistente());

        deteccion.detectar(inconsistente.getId());

        Cliente actualizado = clientes.findById(inconsistente.getId()).orElseThrow();
        assertThat(actualizado.getEstado()).isEqualTo(EstadoPerfil.INCONSISTENTE);
        assertThat(actualizado.getMotivosIncidencia()).isEqualTo("numeroDocumento: no coincide con el formato de NIT");
    }

    // --- Criterio 2: un perfil que cumple todas las reglas queda válido ---

    @Test
    void unPerfilValidoQuedaCompleto() {
        Cliente valido = clientes.save(ejemploValido());

        deteccion.detectar(valido.getId());

        assertThat(clientes.findById(valido.getId()).orElseThrow().getEstado()).isEqualTo(EstadoPerfil.COMPLETO);
    }

    // --- Criterio 3: el listado de revisión, con filtros, muestra solo los perfiles que coinciden ---

    @Test
    void elListadoDeRevisionFiltraPorTipoDeIncidencia() throws Exception {
        Cliente incompleto = clientes.save(ejemploIncompleto());
        Cliente inconsistente = clientes.save(ejemploInconsistente());
        clientes.save(ejemploValido());
        deteccion.detectar(incompleto.getId());
        deteccion.detectar(inconsistente.getId());

        mvc.perform(get("/api/perfil/clientes").param("estado", "INCOMPLETO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.clientes[0].id").value(incompleto.getId()))
                .andExpect(jsonPath("$.clientes[0].estado").value("INCOMPLETO"));

        mvc.perform(get("/api/perfil/clientes").param("estado", "INCONSISTENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.clientes[0].id").value(inconsistente.getId()));

        mvc.perform(get("/api/perfil/clientes").param("estado", "INCONSISTENTE").param("motivo", "numeroDocumento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        mvc.perform(get("/api/perfil/clientes").param("estado", "INCOMPLETO").param("motivo", "numeroDocumento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    // --- Criterio 4: al corregirse, el perfil cambia de estado y queda registro de cuándo y por qué ---

    @Test
    void alCorregirUnPerfilCambiaDeEstadoYQuedaAuditado() {
        Cliente incompleto = clientes.save(ejemploIncompleto());
        deteccion.detectar(incompleto.getId());
        assertThat(clientes.findById(incompleto.getId()).orElseThrow().getEstado()).isEqualTo(EstadoPerfil.INCOMPLETO);

        Cliente aCorregir = clientes.findById(incompleto.getId()).orElseThrow();
        aCorregir.identificar(aCorregir.getNombres(), "Pérez", aCorregir.getTipoDocumento(), aCorregir.getNumeroDocumento());
        clientes.save(aCorregir);

        deteccion.detectar(incompleto.getId());

        Cliente corregido = clientes.findById(incompleto.getId()).orElseThrow();
        assertThat(corregido.getEstado()).isEqualTo(EstadoPerfil.COMPLETO);
        assertThat(corregido.getMotivosIncidencia()).isNull();

        List<CambioPerfil> auditoria = cambiosPerfil.findByIdClienteOrderByFechaAscIdAsc(incompleto.getId());
        assertThat(auditoria).hasSize(2);
        assertThat(auditoria).allSatisfy(cambio -> {
            assertThat(cambio.getOrigen()).isEqualTo(Origen.SISTEMA);
            assertThat(cambio.getResponsable()).isEqualTo("deteccion-automatica");
            assertThat(cambio.getFecha()).isNotNull();
        });
        assertThat(auditoria.get(0).getCambios()).contains("INCOMPLETO");
        assertThat(auditoria.get(1).getCambios()).contains("\"nuevo\":\"COMPLETO\"");
    }

    // --- Perfiles de ejemplo ---

    private static Cliente ejemploIncompleto() {
        Cliente cliente = new Cliente();
        cliente.identificar("Luisa", null, "CI", "6677889");
        cliente.actualizarContacto("luisa@correo.com", null);
        return cliente;
    }

    private static Cliente ejemploInconsistente() {
        Cliente cliente = new Cliente();
        cliente.identificar("Carlos", "Mamani", "NIT", "ABC1234");
        cliente.actualizarContacto("carlos@correo.com", null);
        return cliente;
    }

    private static Cliente ejemploValido() {
        Cliente cliente = new Cliente();
        cliente.identificar("Ana María", "Pérez Rojas", "CI", "4455667");
        cliente.actualizarContacto("ana@correo.com", "+591 70012345");
        cliente.sincronizarDirecciones(Origen.VENTAS, List.of(
                new Direccion.DatosDireccion("D-1", TipoDireccion.ENTREGA, "Av. Arce", null, null, "La Paz", null, true)));
        return cliente;
    }
}
