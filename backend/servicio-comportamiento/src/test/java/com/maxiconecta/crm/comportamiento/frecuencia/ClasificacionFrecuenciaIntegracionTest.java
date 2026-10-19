package com.maxiconecta.crm.comportamiento.frecuencia;

import com.maxiconecta.crm.comportamiento.compra.ClientePerfiles;
import com.maxiconecta.crm.comportamiento.ingesta.EstadoEvento;
import com.maxiconecta.crm.comportamiento.ingesta.EventoRecibidoRepository;
import com.maxiconecta.crm.comportamiento.ingesta.IngestaCompras;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-339 · Clasificación por frecuencia (SCRUM-38, docs/compra/clasificacion-frecuencia.md) con
 * PostgreSQL real, a partir de eventos de compra y anulación: se clasifica con la frecuencia de
 * SCRUM-37, cambia con compras nuevas y anulaciones, suma todos los identificadores del cliente y un
 * cliente sin compras vigentes queda sin clasificación. Umbrales por defecto: 4 y 10.
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ClasificacionFrecuenciaIntegracionTest {

    private static final String RUTA = "/api/comportamiento/clientes/42/indicadores";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private ClientePerfiles perfiles;

    @Autowired
    private IngestaCompras ingesta;

    @Autowired
    private EventoRecibidoRepository eventos;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE comportamiento.cliente_nivel_consumo, comportamiento.cliente_inactivo, "
                + "comportamiento.frecuencia_compra, comportamiento.intento_reproceso, "
                + "comportamiento.evento_procesado, comportamiento.anulacion_item, comportamiento.anulacion, "
                + "comportamiento.compra_item, comportamiento.compra, comportamiento.evento_recibido");
        when(perfiles.buscar(anyString())).thenReturn(Optional.empty());
    }

    // --- Criterio 1: queda como ocasional, habitual o frecuente según los umbrales ---

    @Test
    void unaCompraClasificaAlClienteComoOcasional() throws Exception {
        comprar("CLI-1", 1);

        consultar("CLI-1").andExpect(status().isOk())
                .andExpect(jsonPath("$.clasificacionFrecuencia.clasificacion").value("OCASIONAL"))
                .andExpect(jsonPath("$.clasificacionFrecuencia.frecuencia").value(1))
                .andExpect(jsonPath("$.clasificacionFrecuencia.sinClasificacion").value(false));
    }

    // --- Criterio 2: una compra nueva o una anulación actualiza la clasificación ---

    @Test
    void lasComprasNuevasSubenLaClasificacionAlCruzarCadaUmbral() throws Exception {
        comprar("CLI-1", 3);
        consultar("CLI-1").andExpect(jsonPath("$.clasificacionFrecuencia.clasificacion").value("OCASIONAL"));

        comprar("CLI-1", 1);
        consultar("CLI-1").andExpect(jsonPath("$.clasificacionFrecuencia.clasificacion").value("HABITUAL"))
                .andExpect(jsonPath("$.clasificacionFrecuencia.frecuencia").value(4));

        comprar("CLI-1", 6);
        consultar("CLI-1").andExpect(jsonPath("$.clasificacionFrecuencia.clasificacion").value("FRECUENTE"))
                .andExpect(jsonPath("$.clasificacionFrecuencia.frecuencia").value(10));
    }

    @Test
    void unaAnulacionTotalBajaLaClasificacion() throws Exception {
        String ultima = comprar("CLI-1", 4);
        consultar("CLI-1").andExpect(jsonPath("$.clasificacionFrecuencia.clasificacion").value("HABITUAL"));

        anularTotal(ultima, "CLI-1");

        consultar("CLI-1").andExpect(jsonPath("$.clasificacionFrecuencia.clasificacion").value("OCASIONAL"))
                .andExpect(jsonPath("$.clasificacionFrecuencia.frecuencia").value(3));
    }

    @Test
    void unaDevolucionParcialNoCambiaLaClasificacionPorqueLaCompraSigueVigente() throws Exception {
        String ultima = comprar("CLI-1", 4);

        devolverParcial(ultima, "CLI-1");

        consultar("CLI-1").andExpect(jsonPath("$.clasificacionFrecuencia.clasificacion").value("HABITUAL"))
                .andExpect(jsonPath("$.clasificacionFrecuencia.frecuencia").value(4));
    }

    @Test
    void sumaLaFrecuenciaDeTodosLosIdentificadoresDelCliente() throws Exception {
        comprar("CLI-A", 2);
        comprar("CLI-B", 2);

        mvc.perform(get(RUTA).param("identificador", "CLI-A").param("identificador", "CLI-B"))
                .andExpect(jsonPath("$.clasificacionFrecuencia.clasificacion").value("HABITUAL"))
                .andExpect(jsonPath("$.clasificacionFrecuencia.frecuencia").value(4));
    }

    // --- Criterio 3: sin compras vigentes queda "sin clasificación", no como un error ---

    @Test
    void unClienteSinComprasVigentesQuedaSinClasificacion() throws Exception {
        String unica = comprar("CLI-1", 1);
        anularTotal(unica, "CLI-1");

        consultar("CLI-1").andExpect(status().isOk())
                .andExpect(jsonPath("$.clasificacionFrecuencia.clasificacion").value(nullValue()))
                .andExpect(jsonPath("$.clasificacionFrecuencia.sinClasificacion").value(true));
        consultar("CLI-SIN-COMPRAS").andExpect(status().isOk())
                .andExpect(jsonPath("$.clasificacionFrecuencia.sinClasificacion").value(true));
    }

    private ResultActions consultar(String identificador) throws Exception {
        return mvc.perform(get(RUTA).param("identificador", identificador));
    }

    /** Registra {@code cantidad} compras de 100.00 del cliente y devuelve el id de la última. */
    private String comprar(String idCliente, int cantidad) {
        String idCompra = null;
        for (int i = 0; i < cantidad; i++) {
            idCompra = "V-" + UUID.randomUUID();
            ingesta.recibir("""
                    {
                      "idEvento": "%s",
                      "tipoEvento": "COMPRA_CONFIRMADA",
                      "fechaEmision": "2026-09-20T10:05:00Z",
                      "compra": {
                        "idCompra": "%s",
                        "idCliente": "%s",
                        "fecha": "2026-09-20T10:00:00Z",
                        "montoTotal": 100.00,
                        "items": [ { "categoria": "Electrónica", "cantidad": 1, "monto": 60.00 },
                                   { "categoria": "Accesorios", "cantidad": 1, "monto": 40.00 } ]
                      }
                    }
                    """.formatted(UUID.randomUUID(), idCompra, idCliente));
        }
        exigirProcesados();
        return idCompra;
    }

    private void anularTotal(String idCompra, String idCliente) {
        ingesta.recibirAnulacion(anulacion(idCompra + "-A", idCompra, idCliente, "TOTAL", "100.00", ""));
        exigirProcesados();
    }

    private void devolverParcial(String idCompra, String idCliente) {
        ingesta.recibirAnulacion(anulacion(idCompra + "-D", idCompra, idCliente, "PARCIAL", "40.00",
                ", \"items\": [ { \"categoria\": \"Accesorios\", \"cantidad\": 1, \"monto\": 40.00 } ]"));
        exigirProcesados();
    }

    private void exigirProcesados() {
        assertThat(eventos.findAll()).allMatch(e -> e.getEstado() == EstadoEvento.PROCESADO);
    }

    private static String anulacion(String idAnulacion, String idCompra, String idCliente, String tipo,
                                    String montoRevertido, String items) {
        return """
                {
                  "idEvento": "%s",
                  "tipoEvento": "COMPRA_ANULADA",
                  "fechaEmision": "2026-09-25T10:05:00Z",
                  "anulacion": {
                    "idAnulacion": "%s",
                    "idCompra": "%s",
                    "idCliente": "%s",
                    "tipo": "%s",
                    "fecha": "2026-09-25T10:00:00Z",
                    "montoRevertido": %s,
                    "motivo": "Prueba de clasificación por frecuencia"%s
                  }
                }
                """.formatted(UUID.randomUUID(), idAnulacion, idCompra, idCliente, tipo, montoRevertido, items);
    }
}
