package com.maxiconecta.crm.perfil.validacion;

import com.maxiconecta.crm.perfil.cliente.CambioPerfil;
import com.maxiconecta.crm.perfil.cliente.CambioPerfilRepository;
import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import com.maxiconecta.crm.perfil.cliente.EstadoPerfil;
import com.maxiconecta.crm.perfil.cliente.Origen;
import com.maxiconecta.crm.perfil.cliente.TipoCambio;
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
                + "TRUNCATE perfil.conflicto_perfil, perfil.campo_origen, perfil.cambio_perfil_detalle, "
                + "perfil.cambio_perfil, perfil.direccion, perfil.vinculacion_pendiente, perfil.cliente_origen, "
                + "perfil.cliente, perfil.evento_cliente "
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
        assertThat(actualizado.getActualizadoPorOrigen()).isEqualTo(Origen.CRM);
        assertThat(actualizado.getActualizadoPor()).isEqualTo("deteccion-automatica");

        List<CambioPerfil> registrados = cambiosPerfil.findAll();
        assertThat(registrados).singleElement().satisfies(c -> {
            assertThat(c.getIdCliente()).isEqualTo(cliente.getId());
            assertThat(c.getOrigen()).isEqualTo(Origen.CRM);
            assertThat(c.getResponsable()).isEqualTo("deteccion-automatica");
            assertThat(c.getTipo()).isEqualTo(TipoCambio.DETECCION);
            assertThat(c.getIdEvento()).isNull();
            assertThat(c.getCambios()).contains("\"estado\"").contains("INCOMPLETO");
        });
        assertThat(jdbc.queryForList("SELECT campo FROM perfil.cambio_perfil_detalle ORDER BY orden", String.class))
                .containsExactly("estado", "motivosIncompleto", "motivosIncidencia");

        deteccion.detectar(cliente.getId());

        assertThat(cambiosPerfil.count()).isEqualTo(1);
        assertThat(clientes.findById(cliente.getId()).orElseThrow().getActualizadoEn())
                .isEqualTo(actualizado.getActualizadoEn());
    }

    @Test
    void auditaAmbosMotivosYSuCorreccionSinPerderElHistorico() {
        Cliente cliente = new Cliente();
        cliente.identificar("Ana", null, "NIT", "ABC1234");
        cliente.actualizarContacto("ana@correo.com", null);
        clientes.save(cliente);

        deteccion.detectar(cliente.getId());

        Cliente actualizado = clientes.findById(cliente.getId()).orElseThrow();
        assertThat(actualizado.getEstado()).isEqualTo(EstadoPerfil.INCOMPLETO);
        assertThat(actualizado.getMotivosIncompleto()).isEqualTo("apellidos: vacío");
        assertThat(actualizado.getMotivosInconsistencia()).contains("formato de NIT");
        assertThat(actualizado.getMotivosIncidencia()).contains("apellidos: vacío", "formato de NIT");
        CambioPerfil primeraDeteccion = cambiosPerfil.findAll().get(0);

        actualizado.identificar("Ana", "Pérez", "NIT", "1234567");
        clientes.save(actualizado);
        deteccion.detectar(cliente.getId());

        Cliente corregido = clientes.findById(cliente.getId()).orElseThrow();
        assertThat(corregido.getEstado()).isEqualTo(EstadoPerfil.COMPLETO);
        assertThat(corregido.getMotivosIncompleto()).isNull();
        assertThat(corregido.getMotivosInconsistencia()).isNull();
        assertThat(corregido.getMotivosIncidencia()).isNull();
        assertThat(cambiosPerfil.count()).isEqualTo(2);
        assertThat(cambiosPerfil.findById(primeraDeteccion.getId()).orElseThrow().getCambios())
                .isEqualTo(primeraDeteccion.getCambios());
        assertThat(jdbc.queryForList("SELECT campo FROM perfil.cambio_perfil_detalle "
                + "WHERE id_cambio <> ? ORDER BY orden", String.class, primeraDeteccion.getId()))
                .containsExactly("estado", "motivosIncompleto", "motivosInconsistencia", "motivosIncidencia");
        assertThat(jdbc.queryForObject("SELECT valor_anterior FROM perfil.cambio_perfil_detalle "
                + "WHERE id_cambio <> ? AND campo = 'estado'", String.class, primeraDeteccion.getId()))
                .isEqualTo("INCOMPLETO");
        assertThat(jdbc.queryForObject("SELECT valor_nuevo FROM perfil.cambio_perfil_detalle "
                + "WHERE id_cambio <> ? AND campo = 'estado'", String.class, primeraDeteccion.getId()))
                .isEqualTo("COMPLETO");
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
