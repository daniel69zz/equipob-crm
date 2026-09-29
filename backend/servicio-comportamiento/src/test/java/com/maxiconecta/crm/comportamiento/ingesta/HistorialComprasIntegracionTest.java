package com.maxiconecta.crm.comportamiento.ingesta;

import com.maxiconecta.crm.comportamiento.compra.ClientePerfiles;
import com.maxiconecta.crm.comportamiento.compra.Compra;
import com.maxiconecta.crm.comportamiento.compra.CompraRepository;
import com.maxiconecta.crm.comportamiento.compra.EstadoVinculacionCompra;
import com.maxiconecta.crm.comportamiento.compra.Origen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.Optional;

import static com.maxiconecta.crm.comportamiento.ingesta.LectorEventosTest.ejemplo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** SCRUM-15 · Integridad del historial persistido y su asociación al perfil. */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@Testcontainers(disabledWithoutDocker = true)
class HistorialComprasIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private ClientePerfiles perfiles;

    @Autowired
    private IngestaCompras ingesta;

    @Autowired
    private CompraRepository compras;

    @Autowired
    private EventoRecibidoRepository eventos;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transaccion;

    @BeforeEach
    void limpiarBase() {
        jdbc.execute("TRUNCATE comportamiento.intento_reproceso, comportamiento.evento_procesado, "
                + "comportamiento.anulacion_item, comportamiento.anulacion, "
                + "comportamiento.compra_item, comportamiento.compra, comportamiento.evento_recibido");
    }

    @Test
    void vinculaAmbosCanalesAlMismoPerfilYConservaFechaMontosYCategorias() {
        when(perfiles.buscar(Origen.VENTAS, "CLI-5521")).thenReturn(Optional.of(42L));
        // El identificador se obtiene del evento real de ejemplo, sin asumir que coincide entre canales.
        String marketplace = ejemplo("compra-ventas.json").replace("\"VENTAS\"", "\"MARKETPLACE\"")
                .replace("CLI-5521", "MP-42");
        when(perfiles.buscar(Origen.MARKETPLACE, "MP-42")).thenReturn(Optional.of(42L));

        ingesta.recibir(ejemplo("compra-ventas.json"));
        ingesta.recibir(marketplace);

        assertThat(eventos.findAll()).allMatch(e -> e.getEstado() == EstadoEvento.PROCESADO);
        transaccion.executeWithoutResult(estado -> {
            assertThat(compras.findAll()).hasSize(2).allSatisfy(compra -> {
                assertThat(compra.getIdCliente()).isEqualTo(42L);
                assertThat(compra.getEstadoVinculacion()).isEqualTo(EstadoVinculacionCompra.VINCULADA);
                assertThat(compra.getEstado()).isEqualTo(Compra.CONFIRMADA);
                assertThat(compra.getFecha().toInstant())
                        .isEqualTo(OffsetDateTime.parse("2026-09-27T15:28:10-04:00").toInstant());
                assertThat(compra.getMontoTotal()).isEqualByComparingTo("350.50");
                assertThat(compra.getItems()).extracting(i -> i.getCategoria()).containsExactly("Electrónica", "Accesorios");
                assertThat(compra.getItems()).extracting(i -> i.getCantidad()).containsExactly(1, 2);
                assertThat(compra.getItems().get(0).getMonto()).isEqualByComparingTo("300.00");
                assertThat(compra.getItems().get(1).getMonto()).isEqualByComparingTo("50.50");
                assertThat(eventos.findById(compra.getIdEvento())).isPresent();
            });
        });
    }

    @Test
    void unClienteSinPerfilConservaSuCompraPendienteDeVinculacion() {
        ingesta.recibir(ejemplo("compra-ventas.json"));

        Compra compra = compras.findAll().get(0);
        assertThat(compra.getIdCliente()).isNull();
        assertThat(compra.getEstadoVinculacion()).isEqualTo(EstadoVinculacionCompra.PENDIENTE);
        assertThat(compra.getOrigen()).isEqualTo(Origen.VENTAS);
        assertThat(compra.getIdClienteOrigen()).isEqualTo("CLI-5521");
        assertThat(eventos.findAll().get(0).getEstado()).isEqualTo(EstadoEvento.PROCESADO);
    }

    @Test
    void elReenvioNoDuplicaNiReasignaUnaCompraYaRegistrada() {
        when(perfiles.buscar(Origen.VENTAS, "CLI-5521")).thenReturn(Optional.of(42L));
        ingesta.recibir(ejemplo("compra-ventas.json"));
        when(perfiles.buscar(Origen.VENTAS, "CLI-5521")).thenReturn(Optional.of(99L));

        ingesta.recibir(ejemplo("compra-ventas.json"));

        assertThat(compras.findAll()).singleElement().satisfies(c -> assertThat(c.getIdCliente()).isEqualTo(42L));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comportamiento.compra_item", Long.class)).isEqualTo(2L);
        assertThat(eventos.findAll()).extracting(EventoRecibido::getEstado)
                .containsExactlyInAnyOrder(EstadoEvento.PROCESADO, EstadoEvento.DESCARTADO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"categoria", "fecha", "monto", "suma", "cantidad", "confirmacion"})
    void rechazaDatosInvalidosSinDejarCompraItemsNiClave(String caso) {
        String contenido = ejemplo("compra-ventas.json");
        contenido = switch (caso) {
            case "categoria" -> contenido.replace("Electrónica", " ");
            case "fecha" -> contenido.replace("2026-09-27T15:28:10-04:00", "fecha-invalida");
            case "monto" -> contenido.replace("350.50", "-350.50");
            case "suma" -> contenido.replace("350.50", "351.00");
            case "cantidad" -> contenido.replace("\"cantidad\": 1", "\"cantidad\": 0");
            default -> contenido.replace("COMPRA_CONFIRMADA", "COMPRA_PENDIENTE");
        };
        ingesta.recibir(contenido);

        assertThat(eventos.findAll()).singleElement().satisfies(e -> {
            assertThat(e.getEstado()).isEqualTo(EstadoEvento.FALLIDO);
            assertThat(e.getCausa()).isNotBlank();
        });
        assertThat(compras.count()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comportamiento.compra_item", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comportamiento.evento_procesado", Long.class)).isZero();
    }

    @Test
    void unFalloAlGuardarUnItemRevierteTodaLaCompraYPermiteReintentar() {
        jdbc.execute("""
                CREATE FUNCTION comportamiento.rechazar_item_prueba() RETURNS trigger AS $$
                BEGIN
                    IF NEW.categoria = 'Accesorios' THEN RAISE EXCEPTION 'Fallo de almacenamiento de prueba'; END IF;
                    RETURN NEW;
                END; $$ LANGUAGE plpgsql
                """);
        jdbc.execute("CREATE TRIGGER fallo_item_prueba BEFORE INSERT ON comportamiento.compra_item "
                + "FOR EACH ROW EXECUTE FUNCTION comportamiento.rechazar_item_prueba()");
        try {
            ingesta.recibir(ejemplo("compra-ventas.json"));
            assertThat(eventos.findAll().get(0).getEstado()).isEqualTo(EstadoEvento.FALLIDO);
            assertThat(compras.count()).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM comportamiento.compra_item", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM comportamiento.evento_procesado", Long.class)).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER fallo_item_prueba ON comportamiento.compra_item");
            jdbc.execute("DROP FUNCTION comportamiento.rechazar_item_prueba()");
        }
        ingesta.recibir(ejemplo("compra-ventas.json"));
        assertThat(compras.count()).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE comportamiento.compra SET monto_total = 0",
            "UPDATE comportamiento.compra SET estado_vinculacion = 'VINCULADA'",
            "UPDATE comportamiento.compra SET id_cliente = 42",
            "UPDATE comportamiento.compra_item SET cantidad = 0",
            "UPDATE comportamiento.compra_item SET monto = -1",
            "UPDATE comportamiento.compra_item SET categoria = ' '"
    })
    void laBaseRechazaDatosIncoherentesAunqueSeEscribanSinElProcesador(String sql) {
        ingesta.recibir(ejemplo("compra-ventas.json"));

        assertThatThrownBy(() -> jdbc.execute(sql)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
