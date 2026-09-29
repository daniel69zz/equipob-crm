package com.maxiconecta.crm.perfil.consulta;

import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import com.maxiconecta.crm.perfil.comun.ReglaNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCRUM-167 · Filtros de búsqueda de perfiles con incidencias: motivo y rango de fechas.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ConsultaPerfilTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ClienteRepository clientes;

    @Autowired
    private ConsultaPerfil consulta;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiarBase() {
        jdbc.execute("SET session_replication_role = replica; "
                + "TRUNCATE perfil.cambio_perfil, perfil.direccion, perfil.cliente_origen, perfil.cliente "
                + "RESTART IDENTITY; SET session_replication_role = DEFAULT");
    }

    @Test
    void filtraPorMotivoSinImportarMayusculas() {
        Cliente conApellidos = guardarConMotivo("apellidos: vacío");
        guardarConMotivo("direcciones: ninguna dirección principal");

        List<ResumenPerfil> resultado = consulta
                .buscar(new ConsultaPerfil.FiltroPerfiles(null, null, null, null, null, "APELLIDOS", null, null), 0, 50)
                .getContent();

        assertThat(resultado).extracting(ResumenPerfil::id).containsExactly(conApellidos.getId());
    }

    @Test
    void filtraPorRangoDeFechas() {
        Cliente dentro = guardarConMotivo("apellidos: vacío");
        Cliente fuera = guardarConMotivo("apellidos: vacío");
        actualizarFecha(dentro.getId(), LocalDate.of(2026, 9, 15));
        actualizarFecha(fuera.getId(), LocalDate.of(2026, 8, 1));

        List<ResumenPerfil> resultado = consulta.buscar(new ConsultaPerfil.FiltroPerfiles(null, null, null, null, null,
                null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), 0, 50).getContent();

        assertThat(resultado).extracting(ResumenPerfil::id).containsExactly(dentro.getId());
    }

    @Test
    void desdePosteriorAHastaFalla() {
        assertThatThrownBy(() -> new ConsultaPerfil.FiltroPerfiles(null, null, null, null, null, null,
                LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ReglaNegocioException.class);
    }

    private Cliente guardarConMotivo(String motivo) {
        Cliente cliente = new Cliente();
        cliente.identificar("Ana", null, "CI", "4455667");
        cliente.marcarEstado(List.of(motivo));
        return clientes.save(cliente);
    }

    private void actualizarFecha(Long idCliente, LocalDate fecha) {
        jdbc.update("UPDATE perfil.cliente SET actualizado_en = ? WHERE id = ?",
                fecha.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime(), idCliente);
    }
}
