package com.maxiconecta.crm.comportamiento.evolucion;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-307 · Evolución del consumo por categoría sobre el historial real, con PostgreSQL:
 * clientes que compran en varias categorías y periodos, periodos sin compras, devoluciones
 * parciales y compras anuladas (criterios 1 a 4 de SCRUM-32,
 * docs/compra/evolucion-consumo-categoria.md).
 * <p>
 * La clienta Ana compra con dos identificadores, CLI-5521 y mp-user-3307:
 * <ul>
 *     <li>julio: V-JUL-1 (Hogar 80.00, Limpieza 2 × 20.00) y MP-JUL-2 (Hogar 40.00)</li>
 *     <li>agosto: V-AGO-1 (Electrónica 150.00)</li>
 *     <li>septiembre: V-SEP-1 (Electrónica 300.00, Accesorios 2 × 50.00), el 30 a las 22:00 en La
 *     Paz, que en UTC ya es 1 de octubre</li>
 * </ul>
 * Otro cliente, CLI-9999, compra Hogar en agosto y nunca debe aparecer en la evolución de Ana.
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class EvolucionConsumoIntegracionTest {

    private static final String RUTA = "/api/comportamiento/clientes/42/evolucion-consumo";

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
        jdbc.execute("TRUNCATE comportamiento.cliente_inactivo, comportamiento.frecuencia_compra, "
                + "comportamiento.intento_reproceso, comportamiento.evento_procesado, "
                + "comportamiento.anulacion_item, comportamiento.anulacion, "
                + "comportamiento.compra_item, comportamiento.compra, comportamiento.evento_recibido");
    }

    // --- Criterio 1: monto y cantidad de compras por categoría en cada periodo ---

    @Test
    void muestraElMontoYLasComprasDeCadaCategoriaEnCadaPeriodoDelRango() throws Exception {
        comprasDeAna();

        mvc.perform(consultaDeAna())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value("2026-07-01"))
                .andExpect(jsonPath("$.hasta").value("2026-09-30"))
                .andExpect(jsonPath("$.periodos[*].clave", contains("2026-07", "2026-08", "2026-09")))
                .andExpect(jsonPath("$.periodos[2].fin").value("2026-09-30"))
                .andExpect(jsonPath("$.categorias[*].categoria", contains("Electrónica", "Hogar", "Accesorios", "Limpieza")))
                .andExpect(jsonPath("$.categorias[0].evolucion[*].monto", contains(0, 150.0, 300.0)))
                .andExpect(jsonPath("$.categorias[0].evolucion[*].compras", contains(0, 1, 1)))
                .andExpect(jsonPath("$.categorias[0].monto").value(450.00))
                .andExpect(jsonPath("$.categorias[0].compras").value(2))
                .andExpect(jsonPath("$.categorias[1].evolucion[0].monto").value(120.00))
                .andExpect(jsonPath("$.categorias[1].evolucion[0].compras").value(2))
                .andExpect(jsonPath("$.categorias[3].evolucion[0].unidades").value(2))
                .andExpect(jsonPath("$.totales[*].monto", contains(160.0, 150.0, 400.0)))
                .andExpect(jsonPath("$.totales[*].compras", contains(2, 1, 1)))
                .andExpect(jsonPath("$.categoriasDisponibles", contains("Accesorios", "Electrónica", "Hogar", "Limpieza")))
                .andExpect(jsonPath("$.calidadDatos.comprasAnalizadas").value(4))
                .andExpect(jsonPath("$.calidadDatos.consistente").value(true))
                .andExpect(jsonPath("$.sinDatos").value(false));
    }

    @Test
    void soloIncluyeLasComprasDeLosIdentificadoresDelCliente() throws Exception {
        comprasDeAna();

        // Un identificador repetido no duplica sus compras, y la de CLI-9999 (agosto) nunca entra
        mvc.perform(consultaDeAna().param("identificador", "CLI-5521").param("categoria", "Hogar"))
                .andExpect(jsonPath("$.categorias[0].evolucion[*].monto", contains(120.0, 0, 0)));
        // Solo con CLI-5521 queda fuera MP-JUL-2 (Hogar 40.00)
        mvc.perform(get(RUTA).param("identificador", "CLI-5521")
                        .param("desde", "2026-07-01").param("hasta", "2026-09-30").param("categoria", "Hogar"))
                .andExpect(jsonPath("$.categorias[0].evolucion[*].monto", contains(80.0, 0, 0)));
    }

    @Test
    void comparaCadaPeriodoConElAnteriorYResumeLaTendencia() throws Exception {
        comprasDeAna();

        mvc.perform(consultaDeAna())
                .andExpect(jsonPath("$.periodoBase").value("2026-07"))
                .andExpect(jsonPath("$.categorias[0].evolucion[2].variacionAnterior.monto").value(150.00))
                .andExpect(jsonPath("$.categorias[0].evolucion[2].variacionAnterior.porcentaje").value(100.00))
                .andExpect(jsonPath("$.categorias[0].tendencia").value("CRECE"))
                .andExpect(jsonPath("$.categorias[1].tendencia").value("DEJO_DE_COMPRAR"));
    }

    // --- Criterio 2: filtro por una o varias categorías ---

    @Test
    void conFiltroSoloSeMuestranLasCategoriasElegidas() throws Exception {
        comprasDeAna();

        mvc.perform(consultaDeAna().param("categoria", "Hogar", "Electrónica"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorias[*].categoria", contains("Electrónica", "Hogar")))
                .andExpect(jsonPath("$.totales[*].monto", contains(120.0, 150.0, 300.0)))
                .andExpect(jsonPath("$.categoriasDisponibles.length()").value(4));
    }

    // --- Criterio 3: un periodo sin compras aparece en cero ---

    @Test
    void unPeriodoSinComprasDeUnaCategoriaApareceEnCero() throws Exception {
        comprasDeAna();

        mvc.perform(consultaDeAna().param("categoria", "Hogar"))
                .andExpect(jsonPath("$.categorias[0].evolucion.length()").value(3))
                .andExpect(jsonPath("$.categorias[0].evolucion[1].periodo").value("2026-08"))
                .andExpect(jsonPath("$.categorias[0].evolucion[1].monto").value(0))
                .andExpect(jsonPath("$.categorias[0].evolucion[1].compras").value(0));
    }

    // --- Criterio 4: las compras anuladas no se incluyen ---

    @Test
    void unaCompraAnuladaNoSeIncluyeYSeInformaComoExcluida() throws Exception {
        comprasDeAna();
        anular("V-AGO-1", "CLI-5521", "150.00");

        mvc.perform(consultaDeAna())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorias[0].categoria").value("Electrónica"))
                .andExpect(jsonPath("$.categorias[0].evolucion[*].monto", contains(0, 0, 300.0)))
                .andExpect(jsonPath("$.categorias[0].compras").value(1))
                .andExpect(jsonPath("$.totales[1].compras").value(0))
                .andExpect(jsonPath("$.calidadDatos.comprasAnalizadas").value(3))
                .andExpect(jsonPath("$.calidadDatos.comprasAnuladasExcluidas").value(1));
    }

    @Test
    void unaDevolucionParcialSeDescuentaEnElPeriodoDeLaCompraAunqueOcurraDespues() throws Exception {
        comprasDeAna();
        devolver("V-SEP-1", "CLI-5521", "2026-10-02T10:00:00-04:00", "Accesorios", 1, "50.00");

        mvc.perform(consultaDeAna().param("categoria", "Accesorios", "Electrónica"))
                .andExpect(jsonPath("$.categorias[*].categoria", contains("Electrónica", "Accesorios")))
                .andExpect(jsonPath("$.categorias[1].evolucion[2].monto").value(50.00))
                .andExpect(jsonPath("$.categorias[1].evolucion[2].unidades").value(1))
                .andExpect(jsonPath("$.categorias[0].evolucion[2].monto").value(300.00))
                .andExpect(jsonPath("$.calidadDatos.consistente").value(true));
    }

    // --- Clientes con perfiles de compra distintos ---

    @Test
    void unClienteQueCompraLoMismoCadaMesTieneUnaEvolucionEstable() throws Exception {
        comprar("V-C1", "CLI-7000", "2026-07-10T10:00:00-04:00", "Limpieza:1:50.00");
        comprar("V-C2", "CLI-7000", "2026-08-10T10:00:00-04:00", "Limpieza:1:50.00");
        comprar("V-C3", "CLI-7000", "2026-09-10T10:00:00-04:00", "Limpieza:1:52.00");

        mvc.perform(get(RUTA).param("identificador", "CLI-7000")
                        .param("desde", "2026-07-01").param("hasta", "2026-09-30"))
                .andExpect(jsonPath("$.categorias[0].tendencia").value("ESTABLE"))
                .andExpect(jsonPath("$.categorias[0].evolucion[*].compras", contains(1, 1, 1)));

        // Con el mes en curso todavía sin compras (hasta el 5/09), septiembre es parcial y no la vuelve decreciente
        mvc.perform(get(RUTA).param("identificador", "CLI-7000")
                        .param("desde", "2026-07-01").param("hasta", "2026-09-05"))
                .andExpect(jsonPath("$.periodos[2].parcial").value(true))
                .andExpect(jsonPath("$.categorias[0].evolucion[2].monto").value(0))
                .andExpect(jsonPath("$.categorias[0].tendencia").value("ESTABLE"));
    }

    @Test
    void unClienteSinComprasEnElRangoRecibeLosPeriodosSinCategorias() throws Exception {
        comprasDeAna();

        mvc.perform(get(RUTA).param("identificador", "CLI-SIN-COMPRAS")
                        .param("desde", "2026-07-01").param("hasta", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodos.length()").value(3))
                .andExpect(jsonPath("$.categorias.length()").value(0))
                .andExpect(jsonPath("$.totales[*].compras", contains(0, 0, 0)))
                .andExpect(jsonPath("$.sinDatos").value(true));

        mvc.perform(get(RUTA).param("desde", "2026-07-01").param("hasta", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinDatos").value(true));
    }

    // --- Rango de fechas y zona horaria ---

    @Test
    void unaCompraDelUltimoDiaALaNocheCuentaEnEseDiaSegunLaZonaHorariaDelNegocio() throws Exception {
        comprasDeAna();

        // V-SEP-1 es del 30/09 a las 22:00 en La Paz (1/10 en UTC): entra en un rango que termina el 30/09
        mvc.perform(get(RUTA).param("identificador", "CLI-5521")
                        .param("desde", "2026-09-01").param("hasta", "2026-09-30"))
                .andExpect(jsonPath("$.zonaHoraria").value("America/La_Paz"))
                .andExpect(jsonPath("$.totales[0].monto").value(400.00));
        mvc.perform(get(RUTA).param("identificador", "CLI-5521")
                        .param("desde", "2026-10-01").param("hasta", "2026-10-31"))
                .andExpect(jsonPath("$.sinDatos").value(true));
    }

    @Test
    void agrupaPorTrimestre() throws Exception {
        comprasDeAna();

        mvc.perform(consultaDeAna().param("periodo", "TRIMESTRAL"))
                .andExpect(jsonPath("$.periodos[*].clave", contains("2026-T3")))
                .andExpect(jsonPath("$.categorias[0].evolucion[0].monto").value(450.00))
                .andExpect(jsonPath("$.categorias[0].tendencia").value("SIN_COMPARACION"));
    }

    @Test
    void unRangoInvalidoSeRechazaConUn400() throws Exception {
        mvc.perform(get(RUTA).param("desde", "2026-09-30").param("hasta", "2026-07-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La fecha 'desde' no puede ser posterior a 'hasta'"));
    }

    // --- Datos de prueba ---

    private MockHttpServletRequestBuilder consultaDeAna() {
        return get(RUTA).param("identificador", "CLI-5521", "mp-user-3307")
                .param("desde", "2026-07-01").param("hasta", "2026-09-30");
    }

    private void comprasDeAna() {
        comprar("V-JUL-1", "CLI-5521", "2026-07-05T10:00:00-04:00", "Hogar:1:80.00", "Limpieza:2:40.00");
        comprar("MP-JUL-2", "mp-user-3307", "2026-07-22T18:00:00-04:00", "Hogar:1:40.00");
        comprar("V-AGO-1", "CLI-5521", "2026-08-10T12:00:00-04:00", "Electrónica:1:150.00");
        comprar("V-SEP-1", "CLI-5521", "2026-09-30T22:00:00-04:00", "Electrónica:1:300.00", "Accesorios:2:100.00");
        comprar("V-OTRO-1", "CLI-9999", "2026-08-01T10:00:00-04:00", "Hogar:1:500.00");
    }

    /** Cada ítem es {@code categoria:cantidad:monto}. */
    private void comprar(String idCompra, String idCliente, String fecha, String... items) {
        BigDecimal total = Arrays.stream(items).map(i -> new BigDecimal(i.split(":")[2]))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String json = """
                {
                  "idEvento": "%s",
                  "tipoEvento": "COMPRA_CONFIRMADA",
                  "fechaEmision": "%s",
                  "compra": {
                    "idCompra": "%s",
                    "idCliente": "%s",
                    "fecha": "%s",
                    "montoTotal": %s,
                    "items": [%s]
                  }
                }
                """.formatted(UUID.randomUUID(), fecha, idCompra, idCliente, fecha, total.toPlainString(),
                Arrays.stream(items).map(EvolucionConsumoIntegracionTest::item).collect(Collectors.joining(", ")));
        ingesta.recibir(json);
        assertThat(eventos.findAll()).allMatch(e -> e.getEstado() == EstadoEvento.PROCESADO);
    }

    private void anular(String idCompra, String idCliente, String monto) {
        ingesta.recibirAnulacion("""
                {
                  "idEvento": "%s",
                  "tipoEvento": "COMPRA_ANULADA",
                  "fechaEmision": "2026-09-30T23:00:00-04:00",
                  "anulacion": {
                    "idAnulacion": "%s-A1",
                    "idCompra": "%s",
                    "idCliente": "%s",
                    "tipo": "TOTAL",
                    "fecha": "2026-09-30T23:00:00-04:00",
                    "montoRevertido": %s,
                    "motivo": "El cliente desistió de la compra"
                  }
                }
                """.formatted(UUID.randomUUID(), idCompra, idCompra, idCliente, monto));
        assertThat(eventos.findAll()).allMatch(e -> e.getEstado() == EstadoEvento.PROCESADO);
    }

    private void devolver(String idCompra, String idCliente, String fecha, String categoria, int cantidad,
                          String monto) {
        ingesta.recibirAnulacion("""
                {
                  "idEvento": "%s",
                  "tipoEvento": "COMPRA_ANULADA",
                  "fechaEmision": "%s",
                  "anulacion": {
                    "idAnulacion": "%s-D1",
                    "idCompra": "%s",
                    "idCliente": "%s",
                    "tipo": "PARCIAL",
                    "fecha": "%s",
                    "montoRevertido": %s,
                    "motivo": "Producto con falla",
                    "items": [{ "categoria": "%s", "cantidad": %d, "monto": %s }]
                  }
                }
                """.formatted(UUID.randomUUID(), fecha, idCompra, idCompra, idCliente, fecha, monto, categoria,
                cantidad, monto));
        assertThat(eventos.findAll()).allMatch(e -> e.getEstado() == EstadoEvento.PROCESADO);
    }

    private static String item(String item) {
        String[] partes = item.split(":");
        return "{ \"categoria\": \"%s\", \"cantidad\": %s, \"monto\": %s }".formatted(partes[0], partes[1], partes[2]);
    }
}
