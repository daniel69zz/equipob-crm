package com.maxiconecta.crm.perfil.sincronizacion;

import com.maxiconecta.crm.perfil.cliente.CambioPerfil;
import com.maxiconecta.crm.perfil.cliente.CambioPerfilRepository;
import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import com.maxiconecta.crm.perfil.cliente.ConflictoPerfil;
import com.maxiconecta.crm.perfil.cliente.ConflictoPerfilRepository;
import com.maxiconecta.crm.perfil.cliente.Direccion;
import com.maxiconecta.crm.perfil.cliente.EstadoPerfil;
import com.maxiconecta.crm.perfil.cliente.Origen;
import com.maxiconecta.crm.perfil.configuracion.ConfiguracionRabbit;
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
import java.util.List;

import static com.maxiconecta.crm.perfil.Ejemplos.ejemplo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-163 · Actualización automática del perfil ante notificaciones simuladas de Marketplace y Ventas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ActualizacionAnteCambiosIntegracionTest {

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
    private CambioPerfilRepository cambios;

    @Autowired
    private ConflictoPerfilRepository conflictos;

    @Autowired
    private VinculacionIdentificadores vinculacion;

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
                + "TRUNCATE perfil.conflicto_perfil, perfil.campo_origen, perfil.cambio_perfil, perfil.direccion, "
                + "perfil.vinculacion_pendiente, perfil.cliente_origen, perfil.cliente, perfil.evento_cliente "
                + "RESTART IDENTITY; SET session_replication_role = DEFAULT");
    }

    // --- Criterios 1 y 2: se actualizan solo los campos notificados y el resto se conserva ---

    @Test
    void unaNotificacionConSoloElCorreoCambiaSoloElCorreo() {
        Long idAna = alta("cliente-ventas-alta.json");
        publicar(ejemplo("cliente-ventas-cambio-correo.json"));

        EventoCliente evento = esperar(2).get(1);
        assertThat(evento.getEstado()).isEqualTo(EstadoEventoCliente.PROCESADO);
        transaccion.executeWithoutResult(t -> {
            Cliente ana = clientes.findById(idAna).orElseThrow();
            assertThat(ana.getEmail()).isEqualTo("ana.perez.nuevo@correo.com");
            assertThat(ana.getNombres()).isEqualTo("Ana María");
            assertThat(ana.getNumeroDocumento()).isEqualTo("4455667");
            assertThat(ana.getTelefono()).isEqualTo("+59170012345");
            assertThat(ana.getDireccionesActivas()).extracting(Direccion::getIdDireccionOrigen).containsExactly("D-1");
            assertThat(ana.getEstado()).isEqualTo(EstadoPerfil.COMPLETO);
        });
        CambioPerfil cambio = ultimoCambio(idAna);
        assertThat(cambio.getCambios()).contains("\"campo\":\"email\"").doesNotContain("nombres", "telefono", "direcciones");
    }

    @Test
    void unCampoQueLlegaNuloSeBorraYElPerfilQuedaIncompleto() {
        Long idAna = alta("cliente-ventas-alta.json");
        publicar(parcialDeVentas("\"apellidos\": null"));

        EventoCliente evento = esperar(2).get(1);
        assertThat(evento.getEstado()).isEqualTo(EstadoEventoCliente.INCOMPLETO);
        assertThat(evento.getCausa()).contains("apellidos: vacío");
        Cliente ana = clientes.findById(idAna).orElseThrow();
        assertThat(ana.getApellidos()).isNull();
        assertThat(ana.getNombres()).isEqualTo("Ana María");
    }

    @Test
    void lasDireccionesSoloCambianSiLaNotificacionLasTrae() {
        Long idAna = alta("cliente-ventas-alta.json");
        publicar(parcialDeVentas("\"nombres\": \"Ana María\""));
        esperar(2);
        publicar(parcialDeVentas("\"direcciones\": [{\"idDireccion\": \"D-7\", \"calle\": \"Av. Arce\", "
                + "\"ciudad\": \"la paz\"}]", "2026-09-28T09:00:00-04:00"));
        esperar(3);

        transaccion.executeWithoutResult(t -> assertThat(clientes.findById(idAna).orElseThrow().getDireccionesActivas())
                .extracting(d -> d.getIdDireccionOrigen() + " " + d.getCiudad()).containsExactly("D-7 La Paz"));
    }

    @Test
    void unCambioQueSoloEsDeFormatoNoCuentaComoCambio() {
        Long idAna = alta("cliente-ventas-alta.json");
        publicar(parcialDeVentas("\"nombres\": \"  ANA   MARÍA \", \"contacto\": {\"telefono\": \"+591 700-12345\"}"));

        EventoCliente evento = esperar(2).get(1);
        assertThat(evento.getEstado()).isEqualTo(EstadoEventoCliente.PROCESADO);
        assertThat(evento.getCausa()).isEqualTo("Sin cambios en el perfil");
        assertThat(cambios.findByIdClienteOrderByFechaAscIdAsc(idAna)).hasSize(1);
    }

    // --- Criterio 3: valores distintos de dos sistemas se resuelven con la regla de prioridad y queda la traza ---

    @Test
    void unConflictoEntreSistemasSeResuelveConLaReglaYQuedaLaTraza() throws Exception {
        Long idAna = anaEnVentasYMarketplace();
        publicar(ejemplo("cliente-marketplace-ana-cambios.json"));

        EventoCliente evento = esperar(3).get(2);
        assertThat(evento.getEstado()).isEqualTo(EstadoEventoCliente.PROCESADO);
        assertThat(evento.getCausa()).startsWith("2 conflicto(s) resuelto(s)")
                .contains("nombres: CONSERVADO (PRIORIDAD_SISTEMA)", "telefono: APLICADO (MAS_RECIENTE)");

        Cliente ana = clientes.findById(idAna).orElseThrow();
        assertThat(ana.getNombres()).isEqualTo("Ana María");
        assertThat(ana.getTelefono()).isEqualTo("+59171122333");

        List<ConflictoPerfil> traza = conflictos.findByIdClienteOrderByFechaDescIdDesc(idAna);
        assertThat(traza).extracting(c -> c.getCampo() + " " + c.getOrigenActual() + "→" + c.getOrigenRecibido() + " "
                        + c.getDecision())
                .containsExactlyInAnyOrder("nombres VENTAS→MARKETPLACE CONSERVADO", "telefono VENTAS→MARKETPLACE APLICADO");
        assertThat(traza).allMatch(c -> evento.getId().equals(c.getIdEvento()));

        mvc.perform(get("/api/perfil/clientes/{id}/conflictos", idAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.campo == 'nombres')].valorRecibido").value("Anita"));
    }

    @Test
    void siVentasCorrigeElNombreLuegoGanaVentas() {
        Long idAna = anaEnVentasYMarketplace();
        publicar(parcialDeMarketplace("\"nombres\": \"Anita\"", "2026-09-28T08:00:00-04:00"));
        esperar(3);
        publicar(parcialDeVentas("\"nombres\": \"Ana Lucía\"", "2026-09-28T09:00:00-04:00"));

        assertThat(esperar(4).get(3).getEstado()).isEqualTo(EstadoEventoCliente.PROCESADO);
        assertThat(clientes.findById(idAna).orElseThrow().getNombres()).isEqualTo("Ana Lucía");
    }

    @Test
    void unCambioDeContactoMasAntiguoQueElActualSeConserva() {
        Long idAna = anaEnVentasYMarketplace();
        publicar(parcialDeVentas("\"contacto\": {\"email\": \"reciente@correo.com\"}", "2026-09-28T12:00:00-04:00"));
        esperar(3);
        publicar(parcialDeMarketplace("\"contacto\": {\"email\": \"anterior@correo.com\"}", "2026-09-28T08:00:00-04:00"));

        EventoCliente evento = esperar(4).get(3);
        assertThat(evento.getCausa()).contains("email: CONSERVADO (MAS_RECIENTE)");
        assertThat(clientes.findById(idAna).orElseThrow().getEmail()).isEqualTo("reciente@correo.com");
    }

    // --- Criterio 4 y trazabilidad: el resultado final de cada notificación queda registrado ---

    @Test
    void laTrazaDeSincronizacionMuestraCadaNotificacionConSuResultadoEIntentos() throws Exception {
        Long idAna = alta("cliente-ventas-alta.json");
        publicar(ejemplo("cliente-ventas-cambio-correo.json"));
        esperar(2);

        mvc.perform(get("/api/perfil/clientes/{id}/sincronizaciones", idAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tipoEvento").value("CLIENTE_ACTUALIZADO"))
                .andExpect(jsonPath("$[0].estado").value("PROCESADO"))
                .andExpect(jsonPath("$[0].intentos").value(1));
        mvc.perform(get("/api/perfil/clientes/{id}/sincronizaciones", 999_999)).andExpect(status().isNotFound());
    }

    // --- Utilidades ---

    private Long alta(String ejemplo) {
        publicar(ejemplo(ejemplo));
        return esperar(1).get(0).getIdCliente();
    }

    /**
     * Ana dada de alta en Ventas y su identificador de Marketplace vinculado por un administrador,
     * con el alta de Marketplace (mismos datos, sin conflictos) ya aplicada.
     */
    private Long anaEnVentasYMarketplace() {
        Long idAna = alta("cliente-ventas-alta.json");
        publicar(ejemplo("cliente-marketplace-ana.json").replace("anita.perez@correo.com", "ana.perez@correo.com"));
        esperar(2);
        vinculacion.vincular(idAna, Origen.MARKETPLACE, "mp-user-9001", "admin").forEach(sincronizacion::procesar);
        return idAna;
    }

    private static String parcialDeVentas(String campos) {
        return parcialDeVentas(campos, "2026-09-27T09:00:00-04:00");
    }

    private static String parcialDeVentas(String campos, String fecha) {
        return parcial("VENTAS", "CLI-5521", campos, fecha);
    }

    private static String parcialDeMarketplace(String campos, String fecha) {
        return parcial("MARKETPLACE", "mp-user-9001", campos, fecha);
    }

    private static String parcial(String origen, String idCliente, String campos, String fecha) {
        return """
                {"idEvento": "%s", "tipoEvento": "CLIENTE_ACTUALIZADO", "origen": "%s", "fechaEmision": "%s",
                 "cliente": {"idCliente": "%s", "fechaActualizacion": "%s", %s}}
                """.formatted(java.util.UUID.randomUUID(), origen, fecha, idCliente, fecha, campos);
    }

    private CambioPerfil ultimoCambio(Long idCliente) {
        List<CambioPerfil> registro = cambios.findByIdClienteOrderByFechaAscIdAsc(idCliente);
        return registro.get(registro.size() - 1);
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
