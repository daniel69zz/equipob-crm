package com.maxiconecta.crm.perfil.historial;

import com.maxiconecta.crm.perfil.cliente.CambioPerfil;
import com.maxiconecta.crm.perfil.cliente.CambioPerfilDetalle;
import com.maxiconecta.crm.perfil.cliente.CambioPerfilRepository;
import com.maxiconecta.crm.perfil.cliente.Origen;
import com.maxiconecta.crm.perfil.cliente.TipoCambio;
import com.maxiconecta.crm.perfil.configuracion.ConfiguracionRabbit;
import com.maxiconecta.crm.perfil.sincronizacion.EstadoEventoCliente;
import com.maxiconecta.crm.perfil.sincronizacion.EventoCliente;
import com.maxiconecta.crm.perfil.sincronizacion.EventoClienteRepository;
import com.maxiconecta.crm.perfil.sincronizacion.SincronizacionClientes;
import com.maxiconecta.crm.perfil.vinculacion.ConsolidacionIdentificadores;
import com.maxiconecta.crm.perfil.vinculacion.VinculacionIdentificadores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.maxiconecta.crm.perfil.Ejemplos.ejemplo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-236 · Registro histórico de cambios del cliente (docs/perfil/historico-cambios.md).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class HistoricoCambiosIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private EventoClienteRepository eventos;

    @Autowired
    private CambioPerfilRepository cambios;

    @Autowired
    private VinculacionIdentificadores vinculacion;

    @Autowired
    private ConsolidacionIdentificadores consolidacion;

    @Autowired
    private SincronizacionClientes sincronizacion;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transaccion;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void limpiarBase() {
        jdbc.execute("SET session_replication_role = replica; "
                + "TRUNCATE perfil.consentimiento_historial, perfil.consentimiento_alcance, perfil.consentimiento, perfil.cambio_perfil_detalle, perfil.cambio_perfil, "
                + "perfil.direccion, perfil.vinculacion_pendiente, perfil.cliente_origen, perfil.cliente, "
                + "perfil.evento_cliente RESTART IDENTITY; SET session_replication_role = DEFAULT");
    }

    // --- DoD: cada actualización del perfil genera exactamente un registro histórico ---

    @Test
    void cadaActualizacionGeneraExactamenteUnRegistro() {
        Long idAna = alta();
        assertThat(registros(idAna)).isEqualTo(1);

        publicar(parcialDeVentas("\"contacto\": {\"email\": \"uno@correo.com\"}", "2026-09-27T09:00:00-04:00"));
        esperar(2);
        assertThat(registros(idAna)).isEqualTo(2);

        publicar(parcialDeVentas("\"nombres\": \"Ana Lucía\", \"contacto\": {\"telefono\": \"71122333\"}, "
                + "\"direcciones\": []", "2026-09-27T10:00:00-04:00"));
        esperar(3);
        assertThat(registros(idAna)).isEqualTo(3);
        assertThat(ultimo(idAna).getDetalles()).extracting(CambioPerfilDetalle::getCampo)
                .containsExactly("nombres", "telefono", "direcciones[D-1].principal", "direcciones[D-1].activa");
    }

    @Test
    void unEventoQueNoCambiaNadaNoGeneraRegistro() {
        Long idAna = alta();
        publicar(parcialDeVentas("\"contacto\": {\"email\": \"ANA.PEREZ@correo.com\"}", "2026-09-27T09:00:00-04:00"));
        publicar(ejemplo("cliente-ana-alta.json"));
        esperar(3);

        assertThat(registros(idAna)).isEqualTo(1);
    }

    // --- Criterio 1: se registra el campo, el valor anterior, el nuevo, la fecha y el origen ---

    @Test
    void cadaCampoModificadoQuedaConSuValorAnteriorYNuevo() {
        Long idAna = alta();
        publicar(parcialDeVentas("\"contacto\": {\"email\": \"nuevo@correo.com\"}", "2026-09-27T09:00:00-04:00"));
        esperar(2);

        transaccion.executeWithoutResult(t -> {
            CambioPerfil cambio = ultimo(idAna);
            assertThat(cambio.getTipo()).isEqualTo(TipoCambio.ACTUALIZACION);
            assertThat(cambio.getOrigen()).isEqualTo(Origen.MARKETPLACE_VENTAS);
            assertThat(cambio.getFecha()).isNotNull();
            assertThat(cambio.getDetalles()).singleElement().satisfies(d -> {
                assertThat(d.getCampo()).isEqualTo("email");
                assertThat(d.getValorAnterior()).isEqualTo("ana.perez@correo.com");
                assertThat(d.getValorNuevo()).isEqualTo("nuevo@correo.com");
            });
        });
    }

    // --- Criterio 2: el origen distingue a Marketplace y Ventas de las acciones hechas en el CRM ---

    @Test
    void elOrigenDistingueVentasMarketplaceYElCrm() {
        Long idAna = alta();
        publicar(ejemplo("cliente-carlos-alta.json"));
        Long idCarlos = esperar(2).get(1).getIdCliente();
        publicar(ejemplo("cliente-ana-otra-cuenta.json"));
        esperar(3);
        vinculacion.vincular(idAna, "mp-user-9001", "admin").forEach(sincronizacion::procesar);
        consolidacion.consolidar(idCarlos, idAna, "supervisor");

        assertThat(cambios.findByIdClienteOrderByFechaAscIdAsc(idAna))
                .extracting(c -> c.getTipo() + " " + c.getOrigen() + " " + c.getResponsable())
                .containsExactly("CREACION MARKETPLACE_VENTAS vendedor.jperez", "VINCULACION CRM admin",
                        "ACTUALIZACION MARKETPLACE_VENTAS sincronizacion-automatica", "UNIFICACION CRM supervisor");
        assertThat(cambios.findByIdClienteOrderByFechaAscIdAsc(idCarlos)).extracting(CambioPerfil::getTipo)
                .containsExactly(TipoCambio.CREACION, TipoCambio.UNIFICACION);
    }

    // --- Criterio 3: un usuario autorizado ve los cambios del más reciente al más antiguo ---

    @Test
    void laConsultaMuestraLosCambiosDelMasRecienteAlMasAntiguo() throws Exception {
        Long idAna = alta();
        publicar(parcialDeVentas("\"contacto\": {\"email\": \"uno@correo.com\"}", "2026-09-27T09:00:00-04:00"));
        esperar(2);
        publicar(parcialDeVentas("\"contacto\": {\"email\": \"dos@correo.com\"}", "2026-09-27T10:00:00-04:00"));
        esperar(3);

        mvc.perform(get("/api/perfil/clientes/{id}/historial-cambios", idAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.cambios[0].campos[0].anterior").value("uno@correo.com"))
                .andExpect(jsonPath("$.cambios[0].campos[0].nuevo").value("dos@correo.com"))
                .andExpect(jsonPath("$.cambios[1].campos[0].nuevo").value("uno@correo.com"))
                .andExpect(jsonPath("$.cambios[2].tipo").value("CREACION"))
                .andExpect(jsonPath("$.cambios[2].origen").value("MARKETPLACE_VENTAS"))
                .andExpect(jsonPath("$.cambios[2].responsable").value("vendedor.jperez"));
    }

    @Test
    void laConsultaFiltraPorCampoOrigenYFecha() throws Exception {
        Long idAna = alta();
        publicar(parcialDeVentas("\"contacto\": {\"email\": \"uno@correo.com\"}", "2026-09-27T09:00:00-04:00"));
        esperar(2);
        publicar(ejemplo("cliente-ana-otra-cuenta.json"));
        esperar(3);
        vinculacion.vincular(idAna, "mp-user-9001", "admin").forEach(sincronizacion::procesar);
        String hoy = LocalDate.now().toString();

        // Alta, cambio de correo y el correo que trajo la otra cuenta de Ana al vincularse.
        mvc.perform(get("/api/perfil/clientes/{id}/historial-cambios", idAna).param("campo", "email"))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.cambios[0].campos.length()").value(1))
                .andExpect(jsonPath("$.cambios[0].campos[0].campo").value("email"));
        mvc.perform(get("/api/perfil/clientes/{id}/historial-cambios", idAna).param("campo", "direcciones"))
                .andExpect(jsonPath("$.cambios[0].campos[0].campo").value("direcciones[addr-501].tipo"));
        mvc.perform(get("/api/perfil/clientes/{id}/historial-cambios", idAna).param("origen", "CRM"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.cambios[0].tipo").value("VINCULACION"))
                .andExpect(jsonPath("$.cambios[0].campos[0].nuevo").value("mp-user-9001"));
        mvc.perform(get("/api/perfil/clientes/{id}/historial-cambios", idAna).param("desde", hoy).param("hasta", hoy))
                .andExpect(jsonPath("$.total").value(4));
        mvc.perform(get("/api/perfil/clientes/{id}/historial-cambios", idAna).param("hasta", "2026-01-01"))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/perfil/clientes/{id}/historial-cambios", idAna)
                        .param("desde", "2026-09-30").param("hasta", "2026-09-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/perfil/clientes/{id}/historial-cambios", 999_999)).andExpect(status().isNotFound());
    }

    // --- Criterio 4: el histórico es de solo lectura ---

    @Test
    void elHistoricoNoSePuedeModificarNiBorrar() throws Exception {
        Long idAna = alta();

        assertThatThrownBy(() -> jdbc.update("UPDATE perfil.cambio_perfil_detalle SET valor_nuevo = 'otro'"))
                .hasMessageContaining("solo lectura");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM perfil.cambio_perfil_detalle"))
                .hasMessageContaining("solo lectura");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE perfil.cambio_perfil_detalle CASCADE"))
                .hasMessageContaining("solo lectura");
        assertThatThrownBy(() -> jdbc.update("UPDATE perfil.cambio_perfil SET responsable = 'otro'"))
                .hasMessageContaining("solo lectura");

        mvc.perform(put("/api/perfil/clientes/{id}/historial-cambios", idAna)).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/perfil/clientes/{id}/historial-cambios", idAna)).andExpect(status().isMethodNotAllowed());
    }

    // --- Utilidades ---

    private Long alta() {
        publicar(ejemplo("cliente-ana-alta.json"));
        return esperar(1).get(0).getIdCliente();
    }

    private long registros(Long idCliente) {
        return cambios.findByIdClienteOrderByFechaAscIdAsc(idCliente).size();
    }

    private CambioPerfil ultimo(Long idCliente) {
        return transaccion.execute(t -> {
            List<CambioPerfil> registro = cambios.findByIdClienteOrderByFechaAscIdAsc(idCliente);
            CambioPerfil ultimo = registro.get(registro.size() - 1);
            ultimo.getDetalles().size();
            return ultimo;
        });
    }

    private static String parcialDeVentas(String campos, String fecha) {
        return """
                {"idEvento": "%s", "tipoEvento": "CLIENTE_ACTUALIZADO", "fechaEmision": "%s",
                 "responsable": "cajero.mlopez",
                 "cliente": {"idCliente": "CLI-5521", "fechaActualizacion": "%s", %s}}
                """.formatted(UUID.randomUUID(), fecha, fecha, campos);
    }

    private void publicar(String contenido) {
        rabbit.send(ConfiguracionRabbit.EXCHANGE_VENTAS, ConfiguracionRabbit.RUTA_CLIENTE_ACTUALIZADO,
                new Message(contenido.getBytes(StandardCharsets.UTF_8)));
    }

    private List<EventoCliente> esperar(int cantidad) {
        return await().atMost(Duration.ofSeconds(20)).until(() -> eventos.findAll().stream()
                        .sorted((a, b) -> a.getId().compareTo(b.getId())).toList(),
                lista -> lista.size() == cantidad
                        && lista.stream().noneMatch(e -> e.getEstado() == EstadoEventoCliente.RECIBIDO));
    }
}
