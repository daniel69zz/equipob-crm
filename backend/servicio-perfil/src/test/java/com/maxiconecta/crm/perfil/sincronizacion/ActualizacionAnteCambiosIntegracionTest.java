package com.maxiconecta.crm.perfil.sincronizacion;

import com.maxiconecta.crm.perfil.cliente.CambioPerfil;
import com.maxiconecta.crm.perfil.cliente.CambioPerfilRepository;
import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
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
                + "TRUNCATE perfil.consentimiento_historial, perfil.consentimiento_alcance, perfil.consentimiento, perfil.cambio_perfil_detalle, perfil.cambio_perfil, perfil.direccion, "
                + "perfil.vinculacion_pendiente, perfil.cliente_origen, perfil.cliente, perfil.evento_cliente "
                + "RESTART IDENTITY; SET session_replication_role = DEFAULT");
    }

    // --- Criterios 1 y 2: se actualizan solo los campos notificados y el resto se conserva ---

    @Test
    void unaNotificacionConSoloElCorreoCambiaSoloElCorreo() {
        Long idAna = alta("cliente-ana-alta.json");
        publicar(ejemplo("cliente-ana-cambio-correo.json"));

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
        Long idAna = alta("cliente-ana-alta.json");
        publicar(parcialDeCliente("\"apellidos\": null"));

        EventoCliente evento = esperar(2).get(1);
        assertThat(evento.getEstado()).isEqualTo(EstadoEventoCliente.INCOMPLETO);
        assertThat(evento.getCausa()).contains("apellidos: vacío");
        Cliente ana = clientes.findById(idAna).orElseThrow();
        assertThat(ana.getApellidos()).isNull();
        assertThat(ana.getNombres()).isEqualTo("Ana María");
    }

    @Test
    void lasDireccionesSoloCambianSiLaNotificacionLasTrae() {
        Long idAna = alta("cliente-ana-alta.json");
        publicar(parcialDeCliente("\"nombres\": \"Ana María\""));
        esperar(2);
        publicar(parcialDeCliente("\"direcciones\": [{\"idDireccion\": \"D-7\", \"calle\": \"Av. Arce\", "
                + "\"ciudad\": \"la paz\"}]", "2026-09-28T09:00:00-04:00"));
        esperar(3);

        transaccion.executeWithoutResult(t -> assertThat(clientes.findById(idAna).orElseThrow().getDireccionesActivas())
                .extracting(d -> d.getIdDireccionOrigen() + " " + d.getCiudad()).containsExactly("D-7 La Paz"));
    }

    @Test
    void unCambioQueSoloEsDeFormatoNoCuentaComoCambio() {
        Long idAna = alta("cliente-ana-alta.json");
        publicar(parcialDeCliente("\"nombres\": \"  ANA   MARÍA \", \"contacto\": {\"telefono\": \"+591 700-12345\"}"));

        EventoCliente evento = esperar(2).get(1);
        assertThat(evento.getEstado()).isEqualTo(EstadoEventoCliente.PROCESADO);
        assertThat(evento.getCausa()).isEqualTo("Sin cambios en el perfil");
        assertThat(cambios.findByIdClienteOrderByFechaAscIdAsc(idAna)).hasSize(1);
    }

    // --- Criterio 3: las notificaciones de cualquier identificador vinculado actualizan el mismo perfil ---

    @Test
    void unaNotificacionDeOtroIdentificadorVinculadoActualizaElMismoPerfil() {
        Long idAna = anaConDosIdentificadores();
        publicar(ejemplo("cliente-ana-otra-cuenta-cambios.json"));

        EventoCliente evento = esperar(3).get(2);
        assertThat(evento.getEstado()).isEqualTo(EstadoEventoCliente.PROCESADO);
        assertThat(evento.getIdCliente()).isEqualTo(idAna);

        Cliente ana = clientes.findById(idAna).orElseThrow();
        assertThat(ana.getNombres()).isEqualTo("Anita");
        assertThat(ana.getTelefono()).isEqualTo("+59171122333");
        assertThat(clientes.count()).isEqualTo(1);
    }

    @Test
    void cadaIdentificadorDescartaSusNotificacionesObsoletas() {
        Long idAna = anaConDosIdentificadores();
        publicar(parcial("mp-user-9001", "\"nombres\": \"Anita\"", "2026-09-28T09:00:00-04:00"));
        esperar(3);
        publicar(parcial("mp-user-9001", "\"nombres\": \"Ana Lucía\"", "2026-09-28T08:00:00-04:00"));

        assertThat(esperar(4).get(3).getEstado()).isEqualTo(EstadoEventoCliente.DESCARTADO);
        assertThat(clientes.findById(idAna).orElseThrow().getNombres()).isEqualTo("Anita");
    }

    // --- Criterio 4 y trazabilidad: el resultado final de cada notificación queda registrado ---

    @Test
    void laTrazaDeSincronizacionMuestraCadaNotificacionConSuResultadoEIntentos() throws Exception {
        Long idAna = alta("cliente-ana-alta.json");
        publicar(ejemplo("cliente-ana-cambio-correo.json"));
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
     * Ana dada de alta con CLI-5521 y su otra cuenta del módulo (mp-user-9001) vinculada por un
     * administrador, con el alta de esa cuenta (mismos datos) ya aplicada.
     */
    private Long anaConDosIdentificadores() {
        Long idAna = alta("cliente-ana-alta.json");
        publicar(ejemplo("cliente-ana-otra-cuenta.json").replace("anita.perez@correo.com", "ana.perez@correo.com"));
        esperar(2);
        vinculacion.vincular(idAna, "mp-user-9001", "admin").forEach(sincronizacion::procesar);
        return idAna;
    }

    private static String parcialDeCliente(String campos) {
        return parcialDeCliente(campos, "2026-09-27T09:00:00-04:00");
    }

    private static String parcialDeCliente(String campos, String fecha) {
        return parcial("CLI-5521", campos, fecha);
    }

    private static String parcial(String idCliente, String campos, String fecha) {
        return """
                {"idEvento": "%s", "tipoEvento": "CLIENTE_ACTUALIZADO", "fechaEmision": "%s",
                 "cliente": {"idCliente": "%s", "fechaActualizacion": "%s", %s}}
                """.formatted(java.util.UUID.randomUUID(), fecha, idCliente, fecha, campos);
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
