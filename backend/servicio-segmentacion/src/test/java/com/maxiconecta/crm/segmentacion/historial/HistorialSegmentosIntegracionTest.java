package com.maxiconecta.crm.segmentacion.historial;

import com.maxiconecta.crm.segmentacion.asignacion.AsignacionSegmento;
import com.maxiconecta.crm.segmentacion.asignacion.MetricasCliente;
import com.maxiconecta.crm.segmentacion.criterio.Criterio;
import com.maxiconecta.crm.segmentacion.criterio.CriterioService;
import com.maxiconecta.crm.segmentacion.criterio.SolicitudCriterio;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers
class HistorialSegmentosIntegracionTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final AtomicLong CLIENTES = new AtomicLong(10000);
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-10-19T12:00:00Z");

    @DynamicPropertySource static void base(DynamicPropertyRegistry r) {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("seguridad").locations("filesystem:../api-gateway/src/main/resources/db/migration")
                .load().migrate();
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired AsignacionSegmento asignacion;
    @Autowired CriterioService criterios;
    @Autowired HistorialSegmentos historial;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transacciones;
    private Criterio bajo;
    private Criterio alto;

    @BeforeEach void preparar() {
        jdbc.execute("TRUNCATE segmentacion.segmento_cliente, segmentacion.metricas_cliente, segmentacion.evento_procesado");
        jdbc.update("DELETE FROM segmentacion.criterio");
        bajo = crear("Habituales", Criterio.Indicador.FRECUENCIA, "1", "4");
        alto = crear("Frecuentes", Criterio.Indicador.FRECUENCIA, "5", "20");
    }

    @Test void asignacionCambioYRetiroGeneranUnRegistroCadaUnoYSinCambioNoGenera() {
        long cliente = CLIENTES.incrementAndGet();
        aplicar(cliente, 2, 0);
        aplicar(cliente, 3, 1);
        aplicar(cliente, 8, 2);
        aplicar(cliente, 0, 3);
        var filas = historial.consultar(cliente, 0, 20).contenido();
        assertThat(filas).extracting(HistorialSegmentos.Cambio::operacion)
                .containsExactly("SEGMENTO_RETIRADO", "SEGMENTO_CAMBIADO", "SEGMENTO_ASIGNADO");
        assertThat(filas.get(1).segmentoAnterior()).isEqualTo("Habituales");
        assertThat(filas.get(1).segmentoNuevo()).isEqualTo("Frecuentes");
        assertThat(filas.get(1).regla().get("id").asLong()).isEqualTo(alto.id());
        assertThat(filas.get(0).segmentoNuevo()).isNull();
        assertThat(filas.get(0).motivo()).isEqualTo("SIN_CRITERIO_COINCIDENTE");
        assertThat(filas.get(0).criteriosEvaluados()).hasSize(2);
        assertThat(filas).allSatisfy(c -> {
            assertThat(c.fecha()).isNotNull();
            assertThat(c.reglaCompleta()).isTrue();
            assertThat(c.idEvento()).isNotBlank();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria.evento WHERE entidad = 'SEGMENTO_CLIENTE' "
                + "AND entidad_id = ?", Long.class, Long.toString(cliente))).isEqualTo(3);
    }

    @Test void laReglaHistoricaConservaSusValoresAunqueElCriterioSeEditeODesactive() {
        long cliente = CLIENTES.incrementAndGet();
        aplicar(cliente, 2, 0);
        var editado = criterios.editar(bajo.id(), new SolicitudCriterio("Habituales", "Nuevo umbral",
                Criterio.Indicador.FRECUENCIA, Criterio.Condicion.ENTRE,
                BigDecimal.ZERO, new BigDecimal("4"), null, bajo.version()), "admin");
        aplicar(cliente, 2, 1); // Nueva versión de la regla, mismo segmento: no genera cambio.
        criterios.cambiarEstado(editado.id(), false, editado.version(), "admin");
        var pagina = historial.consultar(cliente, 0, 20);
        assertThat(pagina.totalElementos()).isEqualTo(1);
        assertThat(pagina.contenido().get(0).regla().get("version").asLong()).isEqualTo(bajo.version());
        assertThat(pagina.contenido().get(0).regla().get("minimo").decimalValue()).isEqualByComparingTo("1");
    }

    @Test void reentregasYEventosAtrasadosNoAgreganHistorial() {
        long cliente = CLIENTES.incrementAndGet();
        var evento = metricas(UUID.randomUUID().toString(), cliente, 8, 5);
        asignacion.aplicar(evento);
        assertThat(asignacion.aplicar(evento)).isEqualTo(AsignacionSegmento.Resultado.DUPLICADO);
        assertThat(asignacion.aplicar(metricas(UUID.randomUUID().toString(), cliente, 2, 0)))
                .isEqualTo(AsignacionSegmento.Resultado.DESACTUALIZADO);
        assertThat(historial.consultar(cliente, 0, 20).totalElementos()).isEqualTo(1);
        long sinSegmento = CLIENTES.incrementAndGet();
        aplicar(sinSegmento, 0, 0);
        assertThat(historial.consultar(sinSegmento, 0, 20).contenido()).isEmpty();
    }

    @Test void paginaPorClienteYDesempataFechasIdenticasPorIdDescendente() throws Exception {
        long cliente = CLIENTES.incrementAndGet();
        // now() es constante dentro de la transacción: fuerza el empate de fechas de forma real.
        new TransactionTemplate(transacciones).executeWithoutResult(s -> {
            for (int n = 0; n < 23; n++) aplicar(cliente, n % 2 == 0 ? 2 : 8, n);
            aplicar(CLIENTES.incrementAndGet(), 2, 0);
        });
        var primera = historial.consultar(cliente, 0, 20);
        var segunda = historial.consultar(cliente, 1, 20);
        assertThat(primera.totalElementos()).isEqualTo(23);
        assertThat(primera.totalPaginas()).isEqualTo(2);
        assertThat(primera.contenido()).hasSize(20);
        assertThat(segunda.contenido()).hasSize(3);
        assertThat(primera.contenido()).extracting(HistorialSegmentos.Cambio::fecha).containsOnly(primera.contenido().get(0).fecha());
        assertThat(primera.contenido()).extracting(HistorialSegmentos.Cambio::id)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(primera.contenido().get(19).id()).isGreaterThan(segunda.contenido().get(0).id());
        mvc.perform(get(ruta(cliente)).header("X-Usuario", "gerente")
                        .header("X-Usuario-Permisos", "SEGMENTOS_CONSULTAR").param("pagina", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(23))
                .andExpect(jsonPath("$.contenido.length()").value(3));
    }

    @Test void protegeLaConsultaYValidaLaPaginacion() throws Exception {
        String ruta = ruta(CLIENTES.incrementAndGet());
        mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        mvc.perform(get(ruta).header("X-Usuario", "agente").header("X-Usuario-Permisos", "CLIENTE_CONSULTAR"))
                .andExpect(status().isForbidden());
        mvc.perform(get(ruta).header("X-Usuario", "consulta").header("X-Usuario-Permisos", "SEGMENTOS_CONSULTAR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido").isEmpty());
        for (String[] parametro : new String[][]{{"pagina", "-1"}, {"pagina", "abc"}, {"tamanio", "0"}, {"tamanio", "101"}})
            mvc.perform(get(ruta).header("X-Usuario", "consulta").header("X-Usuario-Permisos", "SEGMENTOS_CONSULTAR")
                    .param(parametro[0], parametro[1])).andExpect(status().isBadRequest());
        mvc.perform(get(ruta(0)).header("X-Usuario", "consulta").header("X-Usuario-Permisos", "SEGMENTOS_CONSULTAR"))
                .andExpect(status().isBadRequest());
    }

    @Test void historialNoOfreceOperacionesHttpDeEscritura() throws Exception {
        String ruta = ruta(CLIENTES.incrementAndGet());
        for (String metodo : List.of("POST", "PUT", "PATCH", "DELETE"))
            mvc.perform(request(org.springframework.http.HttpMethod.valueOf(metodo), ruta)
                    .header("X-Usuario", "admin").header("X-Usuario-Permisos", "SEGMENTACION_CONFIGURAR,SEGMENTOS_CONSULTAR"))
                    .andExpect(status().isMethodNotAllowed());
    }

    @Test void laBaseRechazaModificarBorrarYVaciarElHistorial() {
        long cliente = CLIENTES.incrementAndGet();
        aplicar(cliente, 2, 0);
        assertThatThrownBy(() -> jdbc.update("UPDATE segmentacion.historial_segmento SET motivo='alterado' WHERE cliente_id=?", cliente))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("solo lectura");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM segmentacion.historial_segmento WHERE cliente_id=?", cliente))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("solo lectura");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE segmentacion.historial_segmento"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("solo lectura");
        assertThat(historial.consultar(cliente, 0, 20).totalElementos()).isEqualTo(1);
    }

    @Test void falloDelHistorialRevierteSegmentoAuditoriaMetricasYEventoProcesado() {
        long cliente = CLIENTES.incrementAndGet();
        String evento = UUID.randomUUID().toString();
        // Inyecta un fallo real de persistencia para este cliente sin sustituir el repositorio.
        jdbc.execute("ALTER TABLE segmentacion.historial_segmento ADD CONSTRAINT fallo_prueba CHECK (cliente_id <> " + cliente + ")");
        try {
            assertThatThrownBy(() -> asignacion.aplicar(metricas(evento, cliente, 2, 0)))
                    .isInstanceOf(DataAccessException.class);
            for (String tabla : List.of("segmento_cliente", "metricas_cliente", "evento_procesado", "historial_segmento"))
                assertThat(jdbc.queryForObject("SELECT count(*) FROM segmentacion." + tabla + " WHERE cliente_id=?", Long.class, cliente)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria.evento WHERE entidad='SEGMENTO_CLIENTE' AND entidad_id=?",
                    Long.class, Long.toString(cliente))).isZero();
        } finally {
            jdbc.execute("ALTER TABLE segmentacion.historial_segmento DROP CONSTRAINT fallo_prueba");
        }
        asignacion.aplicar(metricas(evento, cliente, 2, 0));
        assertThat(historial.consultar(cliente, 0, 20).totalElementos()).isEqualTo(1);
    }

    @Test void eventosConcurrentesDelMismoClienteNoDuplicanElCambio() throws Exception {
        long cliente = CLIENTES.incrementAndGet();
        var evento = metricas(UUID.randomUUID().toString(), cliente, 2, 0);
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        try {
            Callable<AsignacionSegmento.Resultado> tarea = () -> { inicio.await(); return asignacion.aplicar(evento); };
            var primero = ejecutor.submit(tarea);
            var segundo = ejecutor.submit(tarea);
            inicio.countDown();
            assertThat(List.of(primero.get(15, TimeUnit.SECONDS), segundo.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(AsignacionSegmento.Resultado.APLICADO, AsignacionSegmento.Resultado.DUPLICADO);
            assertThat(historial.consultar(cliente, 0, 20).totalElementos()).isEqualTo(1);
        } finally { ejecutor.shutdownNow(); }
    }

    @Test void conservaLaReglaDeValorYCategoriaConSusMetricas() {
        crear("Valor alto", Criterio.Indicador.VALOR, "1000", "5000");
        var categoria = criterios.crear(new SolicitudCriterio("Lectores", "", Criterio.Indicador.CATEGORIA,
                Criterio.Condicion.CONTIENE, null, null, "Libros", null), "admin");
        long cliente = CLIENTES.incrementAndGet();
        asignacion.aplicar(new MetricasCliente(UUID.randomUUID().toString(), "COMPRA_CONFIRMADA", cliente,
                List.of(), 0L, new BigDecimal("2000"), List.of("Libros"), INICIO, null, null, null));
        asignacion.aplicar(new MetricasCliente(UUID.randomUUID().toString(), "COMPRA_ANULADA", cliente,
                List.of(), 0L, new BigDecimal("100"), List.of("Libros"), INICIO.plusSeconds(1), null, null, null));
        var filas = historial.consultar(cliente, 0, 20).contenido();
        assertThat(filas.get(0).regla().get("id").asLong()).isEqualTo(categoria.id());
        assertThat(filas.get(0).regla().get("categoria").asText()).isEqualTo("Libros");
        assertThat(filas.get(1).regla().get("indicador").asText()).isEqualTo("VALOR");
        assertThat(filas.get(1).metricas().get("valor").decimalValue()).isEqualByComparingTo("2000");
    }

    @Test void clasificacionPorFrecuenciaParticipaEnLaAsignacionYQuedaEnLaReglaHistorica() {
        criterios.cambiarEstado(bajo.id(), false, bajo.version(), "admin");
        criterios.cambiarEstado(alto.id(), false, alto.version(), "admin");
        var clasificacion = criterios.crear(new SolicitudCriterio("Frecuencia clasificada", "",
                Criterio.Indicador.CLASIFICACION_FRECUENCIA, Criterio.Condicion.IGUAL,
                null, null, null, null, "FRECUENTE"), "admin");
        criterios.crear(new SolicitudCriterio("Lectores", "", Criterio.Indicador.CATEGORIA,
                Criterio.Condicion.CONTIENE, null, null, "Libros", null), "admin");
        long cliente = CLIENTES.incrementAndGet();
        asignacion.aplicar(new MetricasCliente(UUID.randomUUID().toString(), "COMPRA_CONFIRMADA", cliente,
                List.of(), 8L, new BigDecimal("100"), List.of("Libros"), INICIO, "FRECUENTE", null, null));
        criterios.editar(clasificacion.id(), new SolicitudCriterio("Frecuencia clasificada", "",
                Criterio.Indicador.CLASIFICACION_FRECUENCIA, Criterio.Condicion.IGUAL,
                null, null, null, clasificacion.version(), "HABITUAL"), "admin");
        asignacion.aplicar(new MetricasCliente(UUID.randomUUID().toString(), "COMPRA_CONFIRMADA", cliente,
                List.of(), 8L, new BigDecimal("100"), List.of("Libros"), INICIO.plusSeconds(1), "FRECUENTE", null, null));
        var filas = historial.consultar(cliente, 0, 20).contenido();
        assertThat(filas).hasSize(2);
        assertThat(filas.get(0).segmentoNuevo()).isEqualTo("Lectores");
        var original = filas.get(1);
        assertThat(original.segmentoNuevo()).isEqualTo("Frecuencia clasificada");
        assertThat(original.regla().get("indicador").asText()).isEqualTo("CLASIFICACION_FRECUENCIA");
        assertThat(original.regla().get("clasificacion").asText()).isEqualTo("FRECUENTE");
        assertThat(original.regla().get("version").asLong()).isEqualTo(clasificacion.version());
        assertThat(original.metricas().get("clasificacionFrecuencia").asText()).isEqualTo("FRECUENTE");
        assertThat(original.politica()).isEqualTo("VALOR_FRECUENCIA_CLASIFICACION_CATEGORIA_V2");
    }

    private Criterio crear(String nombre, Criterio.Indicador indicador, String minimo, String maximo) {
        return criterios.crear(new SolicitudCriterio(nombre, "", indicador, Criterio.Condicion.ENTRE,
                new BigDecimal(minimo), new BigDecimal(maximo), null, null), "admin");
    }
    private void aplicar(long cliente, long frecuencia, int segundo) {
        asignacion.aplicar(metricas(UUID.randomUUID().toString(), cliente, frecuencia, segundo));
    }
    private static MetricasCliente metricas(String evento, long cliente, long frecuencia, int segundo) {
        return new MetricasCliente(evento, "COMPRA_CONFIRMADA", cliente, List.of(), frecuencia,
                new BigDecimal("100"), List.of(), INICIO.plusSeconds(segundo), null, null, null);
    }
    private static String ruta(long cliente) { return "/api/segmentacion/clientes/" + cliente + "/historial-segmentos"; }
}
