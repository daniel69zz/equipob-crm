package com.maxiconecta.crm.perfil.validacion;

import com.maxiconecta.crm.perfil.cliente.CambioPerfil;
import com.maxiconecta.crm.perfil.cliente.CambioPerfilRepository;
import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import com.maxiconecta.crm.perfil.cliente.EstadoPerfil;
import com.maxiconecta.crm.perfil.cliente.Origen;
import com.maxiconecta.crm.perfil.comun.RecursoNoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCRUM-170 · Auditoría de las detecciones sobre perfiles ya guardados.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DeteccionPerfilesTest {

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
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiarBase() {
        jdbc.execute("SET session_replication_role = replica; "
                + "TRUNCATE perfil.cambio_perfil, perfil.direccion, perfil.cliente_origen, perfil.cliente "
                + "RESTART IDENTITY; SET session_replication_role = DEFAULT");
    }

    @Test
    void detectarUnPerfilIncompletoLoMarcaYAuditaElCambio() {
        Cliente cliente = new Cliente();
        cliente.identificar("Ana", null, "CI", "4455667");
        cliente.actualizarContacto("ana@correo.com", null);
        clientes.save(cliente);

        deteccion.detectar(cliente.getId());

        Cliente actualizado = clientes.findById(cliente.getId()).orElseThrow();
        assertThat(actualizado.getEstado()).isEqualTo(EstadoPerfil.INCOMPLETO);
        assertThat(actualizado.getMotivosIncidencia()).isEqualTo("apellidos: vacío");
        assertThat(actualizado.getActualizadoPorOrigen()).isEqualTo(Origen.SISTEMA);
        assertThat(actualizado.getActualizadoPor()).isEqualTo("deteccion-automatica");

        List<CambioPerfil> registrados = cambiosPerfil.findAll();
        assertThat(registrados).singleElement().satisfies(c -> {
            assertThat(c.getIdCliente()).isEqualTo(cliente.getId());
            assertThat(c.getOrigen()).isEqualTo(Origen.SISTEMA);
            assertThat(c.getResponsable()).isEqualTo("deteccion-automatica");
            assertThat(c.getCambios()).contains("\"estado\"").contains("INCOMPLETO");
        });
    }

    @Test
    void detectarUnPerfilYaValidoNoRegistraNada() {
        Cliente cliente = new Cliente();
        cliente.identificar("Ana María", "Pérez Rojas", "CI", "4455667");
        cliente.actualizarContacto("ana@correo.com", null);
        clientes.save(cliente);

        deteccion.detectar(cliente.getId());

        assertThat(clientes.findById(cliente.getId()).orElseThrow().getEstado()).isEqualTo(EstadoPerfil.COMPLETO);
        assertThat(cambiosPerfil.findAll()).isEmpty();
    }

    @Test
    void detectarUnClienteInexistenteFalla() {
        assertThatThrownBy(() -> deteccion.detectar(999L)).isInstanceOf(RecursoNoEncontradoException.class);
    }
}
