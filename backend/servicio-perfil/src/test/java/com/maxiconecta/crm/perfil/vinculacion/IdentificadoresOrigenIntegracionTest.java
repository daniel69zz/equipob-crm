package com.maxiconecta.crm.perfil.vinculacion;

import com.maxiconecta.crm.perfil.cliente.CambioPerfil;
import com.maxiconecta.crm.perfil.cliente.CambioPerfilRepository;
import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteOrigen;
import com.maxiconecta.crm.perfil.cliente.ClienteOrigenRepository;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import com.maxiconecta.crm.perfil.cliente.EstadoVinculacion;
import com.maxiconecta.crm.perfil.cliente.Origen;
import com.maxiconecta.crm.perfil.cliente.VinculacionPendienteRepository;
import com.maxiconecta.crm.perfil.comun.ReglaNegocioException;
import com.maxiconecta.crm.perfil.configuracion.ConfiguracionRabbit;
import com.maxiconecta.crm.perfil.sincronizacion.EstadoEventoCliente;
import com.maxiconecta.crm.perfil.sincronizacion.EventoCliente;
import com.maxiconecta.crm.perfil.sincronizacion.EventoClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-553 · Identificadores de origen: un mismo cliente en Marketplace y en Ventas apunta a un
 * solo perfil, y un evento de un perfil unificado no recrea el duplicado.
 */
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.concurrency=4",
        "spring.rabbitmq.listener.simple.prefetch=1"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class IdentificadoresOrigenIntegracionTest {

    private static final String ID_MARKETPLACE_ANA = "mp-user-9001";

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
    private VinculacionPendienteRepository vinculaciones;

    @Autowired
    private CambioPerfilRepository cambios;

    @Autowired
    private ConsolidacionIdentificadores consolidacion;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void limpiarBase() {
        jdbc.execute("SET session_replication_role = replica; "
                + "TRUNCATE perfil.conflicto_perfil, perfil.campo_origen, perfil.cambio_perfil_detalle, perfil.cambio_perfil, perfil.direccion, perfil.vinculacion_pendiente, perfil.cliente_origen, "
                + "perfil.cliente, perfil.evento_cliente RESTART IDENTITY; "
                + "SET session_replication_role = DEFAULT");
    }

    // --- Criterio 3: un identificador desconocido crea el perfil o queda pendiente de vinculación ---

    @Test
    void unIdentificadorDesconocidoSinCoincidenciasCreaSuPerfil() {
        publicar(ejemplo("cliente-marketplace-alta.json"));

        assertThat(esperar(1).get(0).getEstado()).isEqualTo(EstadoEventoCliente.PROCESADO);
        assertThat(clientes.count()).isEqualTo(1);
        assertThat(vinculaciones.count()).isZero();
    }

    @Test
    void unIdentificadorDesconocidoConElDocumentoDeOtroPerfilQuedaPendiente() throws Exception {
        Long idAna = altaDeAnaEnVentas();
        publicar(ejemplo("cliente-marketplace-ana.json"));
        publicar(conFecha(ejemplo("cliente-marketplace-ana.json"), "2026-09-27T08:00:00-04:00"));

        List<EventoCliente> recibidos = esperar(3);
        assertThat(recibidos.subList(1, 3)).allSatisfy(e -> {
            assertThat(e.getEstado()).isEqualTo(EstadoEventoCliente.PENDIENTE);
            assertThat(e.getIdCliente()).isNull();
        });
        assertThat(clientes.count()).isEqualTo(1);
        assertThat(vinculaciones.findById(new ClienteOrigen.Clave(Origen.MARKETPLACE, ID_MARKETPLACE_ANA)))
                .hasValueSatisfying(v -> {
                    assertThat(v.getEstado()).isEqualTo(EstadoVinculacion.PENDIENTE);
                    assertThat(v.getIdClienteSugerido()).isEqualTo(idAna);
                    assertThat(v.getMotivo()).contains("CI 4455667");
                });

        mvc.perform(get("/api/perfil/vinculaciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idCliente").value(ID_MARKETPLACE_ANA))
                .andExpect(jsonPath("$[0].idClienteSugerido").value(idAna))
                .andExpect(jsonPath("$[0].eventosPendientes").value(2));
    }

    @Test
    void variasCopiasSimultaneasDeUnIdentificadorPendienteNoFallan() {
        altaDeAnaEnVentas();
        String evento = ejemplo("cliente-marketplace-ana.json");
        IntStream.range(0, 6).parallel().forEach(i -> publicar(evento));

        List<EventoCliente> recibidos = esperar(7);
        assertThat(recibidos.subList(1, 7)).allMatch(e -> e.getEstado() == EstadoEventoCliente.PENDIENTE);
        assertThat(vinculaciones.count()).isEqualTo(1);
        assertThat(clientes.count()).isEqualTo(1);
    }

    // --- Criterio 1: con identificadores en Marketplace y en Ventas, cualquiera se aplica al mismo perfil ---

    @Test
    void alVincularElIdentificadorSusEventosPendientesSeAplicanAlMismoPerfil() throws Exception {
        Long idAna = altaDeAnaEnVentas();
        publicar(ejemplo("cliente-marketplace-ana.json"));
        esperar(2);

        mvc.perform(post("/api/perfil/clientes/{id}/identificadores", idAna).header("X-Usuario", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origen\":\"MARKETPLACE\",\"idCliente\":\"" + ID_MARKETPLACE_ANA + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idClienteVinculado").value(idAna))
                .andExpect(jsonPath("$.eventosAplicados[0].estado").value("PROCESADO"));

        assertThat(clientes.count()).isEqualTo(1);
        assertThat(origenes.findByIdClienteOrderByFechaVinculacion(idAna))
                .extracting(v -> v.getOrigen() + "/" + v.getIdClienteOrigen() + " " + v.getMotivoVinculacion())
                .containsExactly("VENTAS/CLI-5521 ALTA_AUTOMATICA", "MARKETPLACE/mp-user-9001 VINCULACION_MANUAL");
        assertThat(vinculaciones.findById(new ClienteOrigen.Clave(Origen.MARKETPLACE, ID_MARKETPLACE_ANA)))
                .hasValueSatisfying(v -> {
                    assertThat(v.getEstado()).isEqualTo(EstadoVinculacion.VINCULADO);
                    assertThat(v.getResueltaPor()).isEqualTo("admin");
                });
        assertThat(clientes.findById(idAna).orElseThrow().getEmail()).isEqualTo("anita.perez@correo.com");

        publicar(conFecha(ejemplo("cliente-ventas-alta.json"), "2026-09-28T09:00:00-04:00")
                .replace("ana.perez@correo.com", "ana.desde.ventas@correo.com"));
        publicar(conFecha(ejemplo("cliente-marketplace-ana.json"), "2026-09-28T10:00:00-04:00")
                .replace("anita.perez@correo.com", "ana.desde.marketplace@correo.com"));

        List<EventoCliente> recibidos = esperar(4);
        assertThat(recibidos.subList(2, 4)).allSatisfy(e -> {
            assertThat(e.getEstado()).isEqualTo(EstadoEventoCliente.PROCESADO);
            assertThat(e.getIdCliente()).isEqualTo(idAna);
        });
        assertThat(clientes.count()).isEqualTo(1);
    }

    @Test
    void laVinculacionQuedaEnElRegistroDeCambiosConElAdministrador() throws Exception {
        Long idAna = altaDeAnaEnVentas();
        vincularMarketplace(idAna).andExpect(status().isCreated());

        CambioPerfil cambio = cambios.findByIdClienteOrderByFechaAscIdAsc(idAna).get(1);
        assertThat(cambio.getOrigen()).isEqualTo(Origen.CRM);
        assertThat(cambio.getResponsable()).isEqualTo("admin");
        assertThat(cambio.getCambios()).contains("identificadoresOrigen", "MARKETPLACE/mp-user-9001");
    }

    @Test
    void siElAdministradorDecideQueEsOtraPersonaSeCreaUnPerfilNuevo() throws Exception {
        Long idAna = altaDeAnaEnVentas();
        publicar(ejemplo("cliente-marketplace-ana.json"));
        esperar(2);

        mvc.perform(post("/api/perfil/vinculaciones/nuevo-perfil").header("X-Usuario", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origen\":\"MARKETPLACE\",\"idCliente\":\"" + ID_MARKETPLACE_ANA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventosAplicados[0].estado").value("PROCESADO"));

        assertThat(clientes.count()).isEqualTo(2);
        Long idNuevo = origenes.findById(new ClienteOrigen.Clave(Origen.MARKETPLACE, ID_MARKETPLACE_ANA))
                .orElseThrow().getIdCliente();
        assertThat(idNuevo).isNotEqualTo(idAna);

        publicar(conFecha(ejemplo("cliente-marketplace-ana.json"), "2026-09-28T10:00:00-04:00"));
        assertThat(esperar(3).get(2).getIdCliente()).isEqualTo(idNuevo);
    }

    @Test
    void noSePuedeVincularUnIdentificadorQueYaTieneOtroPerfil() throws Exception {
        Long idAna = altaDeAnaEnVentas();
        publicar(ejemplo("cliente-marketplace-alta.json"));
        esperar(2);

        mvc.perform(post("/api/perfil/clientes/{id}/identificadores", idAna).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origen\":\"MARKETPLACE\",\"idCliente\":\"mp-user-3307\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("unifique los perfiles")));
        mvc.perform(post("/api/perfil/clientes/{id}/identificadores", idAna).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origen\":\"CRM\",\"idCliente\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/perfil/clientes/{id}/identificadores", idAna).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origen\":\"MARKETPLACE\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/perfil/clientes/{id}/identificadores", 999_999).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origen\":\"MARKETPLACE\",\"idCliente\":\"mp-x\"}"))
                .andExpect(status().isNotFound());
    }

    // --- Criterio 2: al unificar, los identificadores de ambos quedan en el perfil conservado ---

    @Test
    void alUnificarLosIdentificadoresDelAbsorbidoPasanAlConservado() throws Exception {
        Long idAna = altaDeAnaEnVentas();
        publicar(ejemplo("cliente-marketplace-alta.json"));
        Long idCarlos = esperar(2).get(1).getIdCliente();

        List<ClienteOrigen.Clave> movidos = consolidacion.consolidar(idCarlos, idAna, "admin");

        assertThat(movidos).extracting(ClienteOrigen.Clave::toString).containsExactly("MARKETPLACE/mp-user-3307");
        assertThat(origenes.findByIdClienteOrderByFechaVinculacion(idAna))
                .extracting(v -> v.getId().toString() + " " + v.getMotivoVinculacion())
                .containsExactlyInAnyOrder("VENTAS/CLI-5521 ALTA_AUTOMATICA", "MARKETPLACE/mp-user-3307 UNIFICACION");
        assertThat(origenes.findByIdClienteOrderByFechaVinculacion(idCarlos)).isEmpty();
        assertThat(clientes.findById(idCarlos).orElseThrow().getIdClienteConsolidado()).isEqualTo(idAna);

        mvc.perform(get("/api/perfil/clientes/{id}", idCarlos))
                .andExpect(jsonPath("$.idClienteConsolidado").value(idAna))
                .andExpect(jsonPath("$.identificadoresOrigen").isEmpty());
        mvc.perform(get("/api/perfil/clientes").param("origen", "MARKETPLACE").param("idClienteOrigen", "mp-user-3307"))
                .andExpect(jsonPath("$.clientes[0].id").value(idAna));
    }

    @Test
    void unEventoDeUnPerfilUnificadoSeAplicaAlConservadoYNoRecreaElDuplicado() {
        Long idAna = altaDeAnaEnVentas();
        publicar(ejemplo("cliente-marketplace-alta.json"));
        Long idCarlos = esperar(2).get(1).getIdCliente();
        consolidacion.consolidar(idCarlos, idAna, "admin");

        publicar(conFecha(ejemplo("cliente-marketplace-alta.json"), "2026-09-28T10:00:00-04:00"));
        publicar(ejemplo("cliente-marketplace-alta.json"));

        // Con varios consumidores, los dos mensajes pueden anotarse en la bitácora en cualquier orden.
        List<EventoCliente> posteriores = esperar(4).subList(2, 4);
        assertThat(posteriores).filteredOn(e -> e.getContenido().contains("2026-09-28T10:00:00-04:00"))
                .singleElement().satisfies(e -> {
                    assertThat(e.getEstado()).isEqualTo(EstadoEventoCliente.PROCESADO);
                    assertThat(e.getIdCliente()).isEqualTo(idAna);
                });
        assertThat(posteriores).filteredOn(e -> !e.getContenido().contains("2026-09-28T10:00:00-04:00"))
                .singleElement().satisfies(e -> {
                    assertThat(e.getEstado()).isEqualTo(EstadoEventoCliente.DESCARTADO);
                    assertThat(e.getIdCliente()).isEqualTo(idAna);
                });
        assertThat(clientes.count()).isEqualTo(2);
        assertThat(clientes.findAll()).filteredOn(c -> !c.fueConsolidado()).extracting(Cliente::getId)
                .containsExactly(idAna);
    }

    @Test
    void unaUnificacionEncadenadaApuntaAlUltimoPerfilConservado() {
        publicar(ejemplo("cliente-ventas-alta.json"));
        publicar(ejemplo("cliente-marketplace-alta.json"));
        publicar(ejemplo("cliente-incompleto.json"));
        List<EventoCliente> recibidos = esperar(3);
        Long a = recibidos.get(0).getIdCliente();
        Long b = recibidos.get(1).getIdCliente();
        Long c = recibidos.get(2).getIdCliente();

        consolidacion.consolidar(c, b, "admin");
        consolidacion.consolidar(b, a, "admin");

        assertThat(clientes.findById(c).orElseThrow().getIdClienteConsolidado()).isEqualTo(a);
        assertThat(origenes.findByIdClienteOrderByFechaVinculacion(a)).hasSize(3);
        assertThatThrownBy(() -> consolidacion.consolidar(c, a, "admin")).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> consolidacion.consolidar(a, a, "admin")).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void unaVinculacionPendienteSugiereElPerfilConservadoTrasUnaUnificacion() {
        publicar(ejemplo("cliente-marketplace-alta.json"));
        Long idCarlos = esperar(1).get(0).getIdCliente();
        Long idAna = altaDeAnaEnVentas(2);
        publicar(ejemplo("cliente-marketplace-ana.json"));
        esperar(3);

        consolidacion.consolidar(idAna, idCarlos, "admin");

        assertThat(vinculaciones.findById(new ClienteOrigen.Clave(Origen.MARKETPLACE, ID_MARKETPLACE_ANA)))
                .hasValueSatisfying(v -> assertThat(v.getIdClienteSugerido()).isEqualTo(idCarlos));
    }

    @Test
    void unEventoNoPuedeVenirDelPropioCrm() {
        publicar(ejemplo("cliente-ventas-alta.json").replace("\"VENTAS\"", "\"CRM\""));

        EventoCliente evento = esperar(1).get(0);
        assertThat(evento.getEstado()).isEqualTo(EstadoEventoCliente.FALLIDO);
        assertThat(evento.getCausa()).contains("MARKETPLACE o VENTAS");
    }

    // --- Utilidades ---

    private Long altaDeAnaEnVentas() {
        return altaDeAnaEnVentas(1);
    }

    private Long altaDeAnaEnVentas(int eventosEsperados) {
        publicar(ejemplo("cliente-ventas-alta.json"));
        List<EventoCliente> recibidos = esperar(eventosEsperados);
        return recibidos.get(recibidos.size() - 1).getIdCliente();
    }

    private org.springframework.test.web.servlet.ResultActions vincularMarketplace(Long idCliente) throws Exception {
        return mvc.perform(post("/api/perfil/clientes/{id}/identificadores", idCliente).header("X-Usuario", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"origen\":\"MARKETPLACE\",\"idCliente\":\"" + ID_MARKETPLACE_ANA + "\"}"));
    }

    private static String conFecha(String evento, String fecha) {
        return evento.replaceAll("\"fechaActualizacion\": \"[^\"]+\"", "\"fechaActualizacion\": \"" + fecha + "\"");
    }

    private void publicar(String contenido) {
        rabbit.send(ConfiguracionRabbit.EXCHANGE_VENTAS, ConfiguracionRabbit.RUTA_CLIENTE_REGISTRADO,
                new Message(contenido.getBytes(StandardCharsets.UTF_8)));
    }

    private List<EventoCliente> esperar(int cantidad) {
        return await().atMost(Duration.ofSeconds(20)).until(() -> eventos.findAll().stream()
                        .sorted((a, b) -> a.getId().compareTo(b.getId())).toList(),
                lista -> lista.size() == cantidad
                        && lista.stream().noneMatch(e -> e.getEstado() == EstadoEventoCliente.RECIBIDO));
    }
}
