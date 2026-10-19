package com.maxiconecta.crm.segmentacion.simulacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxiconecta.crm.segmentacion.criterio.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import static com.maxiconecta.crm.segmentacion.criterio.Criterio.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.rabbitmq.listener.simple.auto-startup=false", "segmentacion.simulacion.max-clientes=10000"})
@AutoConfigureMockMvc
@Testcontainers
class SimulacionIntegracionTest {
    @Container static PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void base(DynamicPropertyRegistry r) {
        Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .schemas("seguridad").locations("filesystem:../api-gateway/src/main/resources/db/migration").load().migrate();
        r.add("spring.datasource.url", db::getJdbcUrl);
        r.add("spring.datasource.username", db::getUsername);
        r.add("spring.datasource.password", db::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired CriterioService criterios;
    @Autowired SimulacionSegmentacion simulador;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    private static final String RUTA = "/api/segmentacion/criterios/simular";

    @BeforeEach void limpiar() {
        jdbc.update("DELETE FROM segmentacion.segmento_cliente");
        jdbc.update("DELETE FROM segmentacion.metricas_cliente");
        jdbc.update("DELETE FROM segmentacion.evento_procesado");
        jdbc.update("DELETE FROM segmentacion.criterio");
    }

    @Test void simulaAltaYComparaDistribucionSinModificarNingunaTablaNiSecuencia() throws Exception {
        var frecuente = criterios.crear(regla("Frecuentes", Indicador.FRECUENCIA, "5", "9", null), "admin");
        metrica(1, 7, "100"); metrica(2, 1, "10"); metrica(3, 7, "1000");
        asignar(1, frecuente); asignar(3, frecuente);
        var antes = foto();
        var solicitud = new SolicitudSimulacion(null, regla("Alto valor", Indicador.VALOR, "500", "2000", null));
        var resultado = simulador.simular(solicitud);
        assertThat(resultado.clientesEvaluados()).isEqualTo(3);
        assertThat(resultado.clientesQueCambiarian()).isEqualTo(1);
        fila(resultado, "Frecuentes", 2, 1); fila(resultado, "Alto valor", 0, 1); fila(resultado, "Sin segmento", 1, 1);
        mvc.perform(admin(post(RUTA)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(solicitud)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clientesEvaluados").value(3));
        assertThat(foto()).isEqualTo(antes);
    }

    @Test void editarSustituyeLaReglaConservaIdentidadYPermiteGuardarDespues() throws Exception {
        var c = criterios.crear(regla("Frecuentes", Indicador.FRECUENCIA, "5", "9", null), "admin");
        metrica(1, 5, "100"); metrica(2, 10, "100"); asignar(1, c);
        var nueva = regla("Frecuentes", Indicador.FRECUENCIA, "6", "10", c.version());
        var resultado = simulador.simular(new SolicitudSimulacion(c.id(), nueva));
        fila(resultado, "Frecuentes", 1, 1);
        assertThat(resultado.clientesQueCambiarian()).isEqualTo(2);
        assertThat(criterios.obtener(c.id())).isEqualTo(c);
        mvc.perform(admin(put("/api/segmentacion/criterios/" + c.id())).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(nueva))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        // Guardar configura el criterio; no reasigna masivamente a los clientes.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM segmentacion.segmento_cliente", Long.class)).isEqualTo(1);
    }

    @Test void categoriasNormalizadasYPrioridadDeValorAntesQueFrecuenciaYCategoria() {
        var valor = criterios.crear(regla("Valor", Indicador.VALOR, "500", "2000", null), "admin");
        criterios.crear(regla("Frecuencia", Indicador.FRECUENCIA, "5", "9", null), "admin");
        metrica(1, 7, "1000"); metrica(2, 7, "50"); metrica(3, 1, "50");
        jdbc.update("UPDATE segmentacion.metricas_cliente SET categorias=ARRAY[' ELECTRÓNICA ']");
        var cat = new SolicitudCriterio("Electrónica", "", Indicador.CATEGORIA, Condicion.CONTIENE, null, null, "electronica", null);
        var resultado = simulador.simular(new SolicitudSimulacion(null, cat));
        fila(resultado, valor.nombre(), 0, 1); fila(resultado, "Frecuencia", 0, 1); fila(resultado, "Electrónica", 0, 1);
    }

    @Test void simulaUnCriterioPorClasificacionConLaClasificacionDeLasMetricas() {
        metrica(1, 12, "100"); metrica(2, 5, "100"); metrica(3, 0, "0");
        jdbc.update("UPDATE segmentacion.metricas_cliente SET clasificacion_frecuencia='FRECUENTE' WHERE cliente_id=1");
        jdbc.update("UPDATE segmentacion.metricas_cliente SET clasificacion_frecuencia='HABITUAL' WHERE cliente_id=2");
        var regla = new SolicitudCriterio("Frecuentes", "", Indicador.CLASIFICACION_FRECUENCIA, Condicion.IGUAL,
                null, null, null, null, "FRECUENTE");
        var resultado = simulador.simular(new SolicitudSimulacion(null, regla));
        fila(resultado, "Frecuentes", 0, 1); fila(resultado, "Sin segmento", 3, 2);
    }

    @Test void inactivoConservaSuEstadoYNoParticipaEnLaDistribucion() {
        var c = criterios.crear(regla("Inactivo", Indicador.FRECUENCIA, "1", "5", null), "admin");
        c = criterios.cambiarEstado(c.id(), false, c.version(), "admin");
        metrica(1, 3, "100");
        var resultado = simulador.simular(new SolicitudSimulacion(c.id(), regla(c.nombre(), Indicador.FRECUENCIA, "1", "8", c.version())));
        assertThat(resultado.reglaActiva()).isFalse(); fila(resultado, "Inactivo", 0, 0); fila(resultado, "Sin segmento", 1, 1);
    }

    @Test void sinMetricasNoInventaClientesYReportaAsignacionesExcluidas() {
        jdbc.update("INSERT INTO segmentacion.segmento_cliente(cliente_id,segmento) VALUES (99,'Legado')");
        var r = simulador.simular(new SolicitudSimulacion(null, regla("Nuevo", Indicador.VALOR, "0", "5", null)));
        assertThat(r.clientesEvaluados()).isZero(); assertThat(r.asignacionesSinMetricas()).isEqualTo(1);
        assertThat(r.metricasMasAntiguas()).isNull(); fila(r, "Nuevo", 0, 0);
    }

    @Test void segmentoLegadoConMetricasSeMuestraComoActual() {
        metrica(1, 2, "10");
        jdbc.update("INSERT INTO segmentacion.segmento_cliente(cliente_id,segmento) VALUES (1,'Anterior')");
        var r = simulador.simular(new SolicitudSimulacion(null, regla("Nuevo", Indicador.VALOR, "5", "15", null)));
        fila(r, "Anterior", 1, 0); fila(r, "Nuevo", 0, 1);
    }

    @Test void rechazaRangosInvalidosReglasIncompletasYFrecuenciasDecimales() throws Exception {
        for (var regla : new SolicitudCriterio[]{regla("Mal", Indicador.VALOR, "10", "5", null),
                regla("", Indicador.VALOR, "0", "5", null), regla("Mal", Indicador.FRECUENCIA, "1.5", "5", null),
                new SolicitudCriterio("Incompleta", "", null, null, null, null, null, null)}) {
            mvc.perform(admin(post(RUTA)).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(new SolicitudSimulacion(null, regla)))).andExpect(status().isBadRequest());
        }
        mvc.perform(admin(post(RUTA)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test void rechazaNombreDuplicadoYSuperposicionSinEscribir() throws Exception {
        criterios.crear(regla("Existente", Indicador.FRECUENCIA, "5", "9", null), "admin");
        var antes = foto();
        for (var nueva : new SolicitudCriterio[]{regla("Existente", Indicador.VALOR, "10", "20", null),
                regla("Otra", Indicador.FRECUENCIA, "9", "15", null)}) {
            mvc.perform(admin(post(RUTA)).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(new SolicitudSimulacion(null, nueva)))).andExpect(status().isConflict());
        }
        assertThat(foto()).isEqualTo(antes);
    }

    @Test void rechazaVersionObsoletaYCriterioInexistente() throws Exception {
        var c = criterios.crear(regla("Existente", Indicador.VALOR, "10", "20", null), "admin");
        mvc.perform(admin(post(RUTA)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new SolicitudSimulacion(c.id(), regla(c.nombre(), Indicador.VALOR, "20", "30", 99L)))))
                .andExpect(status().isConflict());
        mvc.perform(admin(post(RUTA)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new SolicitudSimulacion(999999L, regla("Nada", Indicador.VALOR, "0", "1", 0L)))))
                .andExpect(status().isNotFound());
    }

    @Test void guardarTrasSimularRevalidaConflictosConcurrentes() {
        var nueva = regla("Borrador", Indicador.VALOR, "10", "20", null);
        simulador.simular(new SolicitudSimulacion(null, nueva));
        criterios.crear(regla("Otro administrador", Indicador.VALOR, "15", "25", null), "otro");
        assertThatThrownBy(() -> criterios.crear(nueva, "admin")).hasMessageContaining("superpone");
    }

    @Test void diezMilClientesEnMenosDeCincoSegundosYLimiteSinResultadoParcial() {
        jdbc.update("""
                INSERT INTO segmentacion.metricas_cliente(cliente_id,frecuencia,valor,calculadas_en,id_evento_origen)
                SELECT x, x % 10, 100, now(), 'prueba-' || x FROM generate_series(1,10000) x
                """);
        var solicitud = new SolicitudSimulacion(null, regla("Mitad", Indicador.FRECUENCIA, "5", "9", null));
        var r = simulador.simular(solicitud);
        assertThat(r.clientesEvaluados()).isEqualTo(10000); fila(r, "Mitad", 0, 5000);
        assertThat(r.duracionMs()).isLessThan(5000);
        metrica(10001, 1, "100");
        assertThatThrownBy(() -> simulador.simular(solicitud)).hasMessageContaining("503").hasMessageContaining("límite");
    }

    @Test void metricasInvalidasNoSeOcultanComoUnaPoblacionVacia() {
        metrica(1, 1, "100");
        jdbc.update("UPDATE segmentacion.metricas_cliente SET categorias=ARRAY[null]::text[]");
        assertThatThrownBy(() -> simulador.simular(new SolicitudSimulacion(null, regla("Nuevo", Indicador.VALOR, "0", "200", null))))
                .hasMessageContaining("409").hasMessageContaining("categorías inválidas");
    }

    @Test void servicioInternoExigeIdentidadRolYPermiso() throws Exception {
        var cuerpo = json.writeValueAsString(new SolicitudSimulacion(null, regla("Nuevo", Indicador.VALOR, "0", "5", null)));
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isUnauthorized());
        for (String rol : new String[]{"AGENTE_ATENCION", "GERENTE_COMERCIAL", "ANALISTA_DATOS"})
            mvc.perform(post(RUTA).header("X-Usuario", "otro").header("X-Usuario-Rol", rol)
                    .header("X-Usuario-Permisos", "SEGMENTACION_CONFIGURAR").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isForbidden());
    }

    private SolicitudCriterio regla(String nombre, Indicador indicador, String min, String max, Long version) {
        return new SolicitudCriterio(nombre, "", indicador, Condicion.ENTRE, new BigDecimal(min), new BigDecimal(max), null, version);
    }
    private void metrica(long id, long frecuencia, String valor) {
        jdbc.update("INSERT INTO segmentacion.metricas_cliente(cliente_id,frecuencia,valor,calculadas_en,id_evento_origen) VALUES (?,?,?,now(),?)",
                id, frecuencia, new BigDecimal(valor), "evento-" + id);
    }
    private void asignar(long id, Criterio c) {
        jdbc.update("INSERT INTO segmentacion.segmento_cliente(cliente_id,segmento,criterio_id,indicador) VALUES (?,?,?,?)", id, c.nombre(), c.id(), c.indicador().name());
    }
    private void fila(ResultadoSimulacion r, String nombre, long actual, long simulado) {
        var fila = r.distribucion().stream().filter(f -> f.segmento().equals(nombre)).findFirst().orElseThrow();
        assertThat(fila.actual()).isEqualTo(actual); assertThat(fila.simulado()).isEqualTo(simulado);
        assertThat(fila.diferencia()).isEqualTo(simulado - actual);
    }
    private Map<String, String> foto() {
        Map<String, String> resultado = new LinkedHashMap<>();
        for (String tabla : new String[]{"segmentacion.criterio", "segmentacion.segmento_cliente", "segmentacion.metricas_cliente", "segmentacion.evento_procesado", "auditoria.evento"})
            resultado.put(tabla, jdbc.queryForObject("SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text,'[]') FROM " + tabla + " t", String.class));
        resultado.put("secuencia", jdbc.queryForObject("SELECT last_value::text || ':' || is_called::text FROM segmentacion.criterio_id_seq", String.class));
        return resultado;
    }
    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder r) {
        return r.header("X-Usuario", "admin").header("X-Usuario-Rol", "ADMINISTRADOR_CRM").header("X-Usuario-Permisos", "SEGMENTACION_CONFIGURAR");
    }
}
