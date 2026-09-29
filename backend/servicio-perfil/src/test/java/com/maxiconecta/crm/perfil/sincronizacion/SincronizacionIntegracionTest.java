package com.maxiconecta.crm.perfil.sincronizacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxiconecta.crm.perfil.cliente.CambioCampo;
import com.maxiconecta.crm.perfil.cliente.CambioPerfil;
import com.maxiconecta.crm.perfil.cliente.CambioPerfilRepository;
import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteOrigen;
import com.maxiconecta.crm.perfil.cliente.ClienteOrigenRepository;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import com.maxiconecta.crm.perfil.cliente.Direccion;
import com.maxiconecta.crm.perfil.cliente.EstadoPerfil;
import com.maxiconecta.crm.perfil.cliente.Origen;
import com.maxiconecta.crm.perfil.cliente.TipoCambio;
import com.maxiconecta.crm.perfil.configuracion.ConfiguracionRabbit;
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
import java.util.List;
import java.util.stream.IntStream;

import static com.maxiconecta.crm.perfil.Ejemplos.ejemplo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-554 · Sincronización del perfil con datos de ejemplo de Marketplace y de Ventas, con
 * PostgreSQL y RabbitMQ reales.
 */
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.concurrency=4",
        "spring.rabbitmq.listener.simple.prefetch=1"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SincronizacionIntegracionTest {

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
    private ClienteRepository clientes;

    @Autowired
    private ClienteOrigenRepository origenes;

    @Autowired
    private CambioPerfilRepository cambios;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transaccion;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void limpiarBase() {
        // El registro de cambios rechaza TRUNCATE: en las pruebas se desactivan los triggers solo para limpiar.
        jdbc.execute("SET session_replication_role = replica; "
                + "TRUNCATE perfil.cambio_perfil, perfil.direccion, perfil.cliente_origen, perfil.cliente, "
                + "perfil.evento_cliente RESTART IDENTITY; "
                + "SET session_replication_role = DEFAULT");
    }

    // --- Criterio 1: un cliente nuevo crea su perfil con identificación, contacto y direcciones ---

    @Test
    void unClienteNuevoDeVentasCreaSuPerfilCompleto() {
        publicar(ejemplo("cliente-ventas-alta.json"));

        EventoCliente evento = esperar(1).get(0);
        assertThat(evento.getEstado()).isEqualTo(EstadoEventoCliente.PROCESADO);
        assertThat(evento.getIdCliente()).isNotNull();
        assertThat(evento.getIdClienteOrigen()).isEqualTo("CLI-5521");

        transaccion.executeWithoutResult(t -> {
            Cliente cliente = clientes.findById(evento.getIdCliente()).orElseThrow();
            assertThat(cliente.getNombres()).isEqualTo("Ana María");
            assertThat(cliente.getApellidos()).isEqualTo("Pérez Rojas");
            assertThat(cliente.getTipoDocumento()).isEqualTo("CI");
            assertThat(cliente.getNumeroDocumento()).isEqualTo("4455667");
            assertThat(cliente.getEmail()).isEqualTo("ana.perez@correo.com");
            assertThat(cliente.getTelefono()).isEqualTo("+591 70012345");
            assertThat(cliente.getEstado()).isEqualTo(EstadoPerfil.COMPLETO);
            assertThat(cliente.getDireccionesActivas()).singleElement().satisfies(d -> {
                assertThat(d.getCalle()).isEqualTo("Av. 6 de Agosto");
                assertThat(d.getCiudad()).isEqualTo("La Paz");
                assertThat(d.isPrincipal()).isTrue();
            });
        });
        assertThat(origenes.findById(new ClienteOrigen.Clave(Origen.VENTAS, "CLI-5521")))
                .hasValueSatisfying(v -> assertThat(v.getIdCliente()).isEqualTo(evento.getIdCliente()));
    }

    @Test
    void unClienteDeMarketplaceCreaOtroPerfil() {
        publicar(ejemplo("cliente-ventas-alta.json"));
        publicar(ejemplo("cliente-marketplace-alta.json"));

        List<EventoCliente> recibidos = esperar(2);
        assertThat(recibidos).allMatch(e -> e.getEstado() == EstadoEventoCliente.PROCESADO);
        assertThat(clientes.count()).isEqualTo(2);
        assertThat(origenes.findById(new ClienteOrigen.Clave(Origen.MARKETPLACE, "mp-user-3307"))).isPresent();
    }

    @Test
    void unaActualizacionDeUnClienteDesconocidoCreaSuPerfil() {
        publicar(ejemplo("cliente-ventas-actualizacion.json"));

        assertThat(esperar(1).get(0).getEstado()).isEqualTo(EstadoEventoCliente.PROCESADO);
        assertThat(clientes.count()).isEqualTo(1);
    }

    // --- Criterio 2: una actualización modifica el mismo perfil, sin duplicarlo ---

    @Test
    void unaActualizacionModificaElMismoPerfilSinDuplicarlo() {
        publicar(ejemplo("cliente-ventas-alta.json"));
        Long idCliente = esperar(1).get(0).getIdCliente();
        publicar(ejemplo("cliente-ventas-actualizacion.json"));

        assertThat(esperar(2)).allMatch(e -> e.getEstado() == EstadoEventoCliente.PROCESADO
                && idCliente.equals(e.getIdCliente()));
        assertThat(clientes.count()).isEqualTo(1);
        transaccion.executeWithoutResult(t -> {
            Cliente cliente = clientes.findById(idCliente).orElseThrow();
            assertThat(cliente.getEmail()).isEqualTo("ana.m.perez@correo.com");
            assertThat(cliente.getDireccionesActivas())
                    .extracting(d -> d.getIdDireccionOrigen() + " " + d.isPrincipal() + " " + d.getReferencia())
                    .containsExactly("D-1 false Edificio Illimani, piso 5", "D-2 true null");
        });
    }

    @Test
    void unEventoMasAntiguoQueElUltimoAplicadoSeDescartaSinCambiarElPerfil() {
        publicar(ejemplo("cliente-ventas-alta.json"));
        esperar(1);
        publicar(ejemplo("cliente-ventas-actualizacion.json"));
        esperar(2);
        publicar(ejemplo("cliente-ventas-obsoleto.json"));

        EventoCliente obsoleto = ultimo(esperar(3));
        assertThat(obsoleto.getEstado()).isEqualTo(EstadoEventoCliente.DESCARTADO);
        assertThat(obsoleto.getCausa()).isEqualTo("Evento obsoleto: el cambio del 2026-09-22T11:30-04:00 de "
                + "VENTAS/CLI-5521 es anterior al último aplicado (2026-09-25T16:40-04:00)");
        Cliente cliente = clientes.findAll().get(0);
        assertThat(cliente.getEmail()).isEqualTo("ana.m.perez@correo.com");
        assertThat(cambios.count()).isEqualTo(2);
    }

    @Test
    void elMismoEventoReentregadoSeDescarta() {
        publicar(ejemplo("cliente-ventas-alta.json"));
        esperar(1);
        publicar(ejemplo("cliente-ventas-alta.json"));

        EventoCliente reentrega = ultimo(esperar(2));
        assertThat(reentrega.getEstado()).isEqualTo(EstadoEventoCliente.DESCARTADO);
        assertThat(reentrega.getCausa()).contains("ya fue aplicado");
        assertThat(clientes.count()).isEqualTo(1);
        assertThat(cambios.count()).isEqualTo(1);
    }

    @Test
    void diezAltasSimultaneasDelMismoClienteDejanUnSoloPerfil() {
        String alta = ejemplo("cliente-marketplace-alta.json");
        IntStream.range(0, 10).parallel().forEach(i -> publicar(alta));

        List<EventoCliente> recibidos = esperar(10);
        assertThat(clientes.count()).isEqualTo(1);
        assertThat(origenes.count()).isEqualTo(1);
        assertThat(recibidos).filteredOn(e -> e.getEstado() == EstadoEventoCliente.PROCESADO).hasSize(1);
        assertThat(recibidos).noneMatch(e -> e.getEstado() == EstadoEventoCliente.FALLIDO);
    }

    @Test
    void unaDireccionQueElOrigenDejaDeInformarSeDesactiva() {
        publicar(ejemplo("cliente-ventas-actualizacion.json"));
        esperar(1);
        publicar(ejemplo("cliente-ventas-actualizacion.json")
                .replace("2026-09-25T16:40:00-04:00", "2026-09-26T08:00:00-04:00")
                .replaceAll("(?s),\\s*\\{\\s*\"idDireccion\": \"D-2\".*?}", ""));

        assertThat(ultimo(esperar(2)).getEstado()).isEqualTo(EstadoEventoCliente.PROCESADO);
        transaccion.executeWithoutResult(t -> {
            Cliente cliente = clientes.findAll().get(0);
            assertThat(cliente.getDireccionesActivas()).extracting(Direccion::getIdDireccionOrigen).containsExactly("D-1");
            assertThat(cliente.getDirecciones()).hasSize(2);
        });
    }

    // --- Criterio 3: un dato obligatorio vacío o mal formado deja el perfil incompleto y el evento registrado ---

    @Test
    void unDatoObligatorioVacioOMalFormadoDejaElPerfilIncompleto() {
        publicar(ejemplo("cliente-incompleto.json"));

        EventoCliente evento = esperar(1).get(0);
        assertThat(evento.getEstado()).isEqualTo(EstadoEventoCliente.INCOMPLETO);
        assertThat(evento.getCausa()).isEqualTo("Perfil incompleto: numeroDocumento: vacío; email: formato inválido; "
                + "direcciones[D-9].ciudad: vacío");
        transaccion.executeWithoutResult(t -> {
            Cliente cliente = clientes.findById(evento.getIdCliente()).orElseThrow();
            assertThat(cliente.getEstado()).isEqualTo(EstadoPerfil.INCOMPLETO);
            assertThat(cliente.getMotivosIncidencia()).contains("numeroDocumento: vacío");
            assertThat(cliente.getNombres()).isEqualTo("Luisa");
            assertThat(cliente.getTelefono()).isEqualTo("+591 76543210");
            assertThat(cliente.getEmail()).isNull();
            assertThat(cliente.getDirecciones()).isEmpty();
        });
    }

    @Test
    void cuandoLlegaElDatoCorrectoElPerfilQuedaCompleto() throws Exception {
        publicar(ejemplo("cliente-incompleto.json"));
        esperar(1);
        publicar(ejemplo("cliente-incompleto.json")
                .replace("2026-09-23T11:59:30-04:00", "2026-09-24T09:00:00-04:00")
                .replace("\"numeroDocumento\": \"\"", "\"numeroDocumento\": \"6677889\"")
                .replace("luisa.fernandez@\"", "luisa.fernandez@correo.com\"")
                .replace("\"calle\": \"Calle Sucre\", \"numero\": \"340\"",
                        "\"calle\": \"Calle Sucre\", \"numero\": \"340\", \"ciudad\": \"Sucre\""));

        EventoCliente correccion = ultimo(esperar(2));
        assertThat(correccion.getEstado()).isEqualTo(EstadoEventoCliente.PROCESADO);
        Cliente cliente = clientes.findById(correccion.getIdCliente()).orElseThrow();
        assertThat(cliente.getEstado()).isEqualTo(EstadoPerfil.COMPLETO);
        assertThat(cliente.getMotivosIncidencia()).isNull();
        assertThat(camposCambiados(ultimoCambio(cliente.getId()))).contains("estado", "numeroDocumento", "email");
    }

    @Test
    void unMensajeSinClienteONoJsonQuedaFallidoConSuContenido() {
        publicar(ejemplo("cliente-sin-id.json"));
        publicar("esto no es json");

        List<EventoCliente> recibidos = esperar(2);
        assertThat(recibidos).allMatch(e -> e.getEstado() == EstadoEventoCliente.FALLIDO);
        assertThat(recibidos).extracting(EventoCliente::getCausa).anyMatch(c -> c.contains("cliente.idCliente"));
        assertThat(recibidos).extracting(EventoCliente::getContenido).contains("esto no es json");
        assertThat(clientes.count()).isZero();
    }

    // --- Criterio 4: cada modificación queda registrada con fecha, origen y responsable ---

    @Test
    void cadaCreacionYModificacionQuedaRegistradaConFechaOrigenYResponsable() throws Exception {
        publicar(ejemplo("cliente-ventas-alta.json"));
        esperar(1);
        publicar(ejemplo("cliente-ventas-actualizacion.json"));
        Long idCliente = esperar(2).get(0).getIdCliente();

        List<CambioPerfil> registro = cambios.findByIdClienteOrderByFechaAscIdAsc(idCliente);
        assertThat(registro).extracting(CambioPerfil::getTipo)
                .containsExactly(TipoCambio.CREACION, TipoCambio.ACTUALIZACION);
        assertThat(registro).allSatisfy(c -> {
            assertThat(c.getFecha()).isNotNull();
            assertThat(c.getOrigen()).isEqualTo(Origen.VENTAS);
        });
        assertThat(registro).extracting(CambioPerfil::getResponsable)
                .containsExactly("vendedor.jperez", "cajero.mlopez");
        assertThat(camposCambiados(registro.get(1))).containsExactlyInAnyOrder("email",
                "direcciones[D-1].referencia", "direcciones[D-1].principal", "direcciones[D-2].tipo",
                "direcciones[D-2].calle", "direcciones[D-2].numero", "direcciones[D-2].zona", "direcciones[D-2].ciudad",
                "direcciones[D-2].principal");
        assertThat(cambioDe(registro.get(1), "email"))
                .isEqualTo(new CambioCampo("email", "ana.perez@correo.com", "ana.m.perez@correo.com"));

        Cliente cliente = clientes.findById(idCliente).orElseThrow();
        assertThat(cliente.getActualizadoPorOrigen()).isEqualTo(Origen.VENTAS);
        assertThat(cliente.getActualizadoPor()).isEqualTo("cajero.mlopez");
    }

    @Test
    void elRegistroDeCambiosNoSePuedeModificarNiBorrar() {
        publicar(ejemplo("cliente-ventas-alta.json"));
        esperar(1);

        assertThatThrownBy(() -> jdbc.update("UPDATE perfil.cambio_perfil SET responsable = 'otro'"))
                .hasMessageContaining("solo lectura");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM perfil.cambio_perfil"))
                .hasMessageContaining("solo lectura");
    }

    // --- Consulta del perfil ---

    @Test
    void elPerfilSeConsultaPorSuIdentificadorYSeBuscaPorOrigenODocumento() throws Exception {
        publicar(ejemplo("cliente-ventas-alta.json"));
        Long idCliente = esperar(1).get(0).getIdCliente();

        mvc.perform(get("/api/perfil/clientes/{id}", idCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombres").value("Ana María"))
                .andExpect(jsonPath("$.estado").value("COMPLETO"))
                .andExpect(jsonPath("$.identificadoresOrigen[0].idCliente").value("CLI-5521"))
                .andExpect(jsonPath("$.direcciones[0].ciudad").value("La Paz"));
        mvc.perform(get("/api/perfil/clientes").param("origen", "VENTAS").param("idClienteOrigen", "CLI-5521"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.clientes[0].id").value(idCliente));
        mvc.perform(get("/api/perfil/clientes").param("tipoDocumento", "ci").param("numeroDocumento", "4455667"))
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/perfil/clientes/{id}", 999_999)).andExpect(status().isNotFound());
        mvc.perform(get("/api/perfil/clientes").param("idClienteOrigen", "CLI-5521")).andExpect(status().isBadRequest());
    }

    // --- Utilidades ---

    private void publicar(String contenido) {
        rabbit.send(ConfiguracionRabbit.EXCHANGE_VENTAS, ConfiguracionRabbit.RUTA_CLIENTE_REGISTRADO,
                new Message(contenido.getBytes(StandardCharsets.UTF_8)));
    }

    /** Espera a que haya tantos eventos en la bitácora y a que ninguno siga en RECIBIDO. */
    private List<EventoCliente> esperar(int cantidad) {
        return await().atMost(Duration.ofSeconds(20)).until(() -> eventos.findAll().stream()
                        .sorted((a, b) -> a.getId().compareTo(b.getId())).toList(),
                lista -> lista.size() == cantidad
                        && lista.stream().noneMatch(e -> e.getEstado() == EstadoEventoCliente.RECIBIDO));
    }

    private static EventoCliente ultimo(List<EventoCliente> lista) {
        return lista.get(lista.size() - 1);
    }

    private CambioPerfil ultimoCambio(Long idCliente) {
        List<CambioPerfil> registro = cambios.findByIdClienteOrderByFechaAscIdAsc(idCliente);
        return registro.get(registro.size() - 1);
    }

    private List<String> camposCambiados(CambioPerfil cambio) throws Exception {
        return leer(cambio).stream().map(CambioCampo::campo).toList();
    }

    private CambioCampo cambioDe(CambioPerfil cambio, String campo) throws Exception {
        return leer(cambio).stream().filter(c -> c.campo().equals(campo)).findFirst().orElseThrow();
    }

    private List<CambioCampo> leer(CambioPerfil cambio) throws Exception {
        return List.of(objectMapper.readValue(cambio.getCambios(), CambioCampo[].class));
    }
}
