package com.maxiconecta.crm.segmentacion.criterio;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import static com.maxiconecta.crm.segmentacion.criterio.Criterio.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CriteriosIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void base(DynamicPropertyRegistry r) {
        // Las migraciones reales del Gateway son dueñas de la auditoría compartida.
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas("seguridad").locations("filesystem:../api-gateway/src/main/resources/db/migration").load().migrate();
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        // Estas pruebas no usan RabbitMQ: la asignación automática se prueba en AsignacionSegmentoIntegracionTest.
        r.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
    }
    @Autowired CriterioService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @BeforeEach void limpiarCriterios() { jdbc.update("DELETE FROM segmentacion.criterio"); }

    @Test void creaLosTresIndicadoresYLosExponeComoDisponibles() throws Exception {
        var f = crear("Frecuentes", Indicador.FRECUENCIA, "5", "9");
        var v = crear("Valor medio", Indicador.VALOR, "500.01", "1000.00");
        var c = service.crear(categoria("Tecnologia", " Electrónica  "), "admin");
        assertThat(service.listar(true, null)).hasSize(3);
        assertThat(service.listar(true, Indicador.CATEGORIA)).containsExactly(c);
        assertThat(f.minimo()).isEqualByComparingTo("5");
        assertThat(v.minimo()).isEqualByComparingTo("500.01");
        mvc.perform(admin(get("/api/segmentacion/criterios").param("activo", "true")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
    }

    @Test void losLimitesSonInclusivosYLaFrecuenciaEsAcumulada() {
        var c = crear("Intervalo", Indicador.FRECUENCIA, "5", "9");
        for (int n : new int[]{5, 6, 9}) assertThat(ReglasCriterio.cumple(c, new BigDecimal(n), null, List.of())).isTrue();
        for (int n : new int[]{0, 4, 10}) assertThat(ReglasCriterio.cumple(c, new BigDecimal(n), null, List.of())).isFalse();
        assertThat(ReglasCriterio.cumple(c, null, null, List.of())).isFalse();
    }

    @Test void rechazaSuperposicionIncluidoUnSoloPuntoDeContacto() {
        crear("Bajo", Indicador.FRECUENCIA, "0", "5");
        assertThatThrownBy(() -> crear("Medio", Indicador.FRECUENCIA, "5", "10"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("superpone").hasMessageContaining("Bajo");
        assertThatThrownBy(() -> crear("Contenido", Indicador.FRECUENCIA, "1", "2")).hasMessageContaining("superpone");
        assertThatThrownBy(() -> crear("Contenedor", Indicador.FRECUENCIA, "0", "50")).hasMessageContaining("superpone");
        crear("Adyacente", Indicador.FRECUENCIA, "6", "10");
        crear("Otro indicador", Indicador.VALOR, "0", "5");
        assertThat(service.listar(null, null)).hasSize(3);
    }

    @Test void admiteIntervalosAbiertosYDetectaSusConflictos() {
        var c = service.crear(new SolicitudCriterio("Alto", "", Indicador.VALOR, Condicion.MAYOR_IGUAL,
                new BigDecimal("1000"), null, null, null), "admin");
        assertThat(ReglasCriterio.cumple(c, null, new BigDecimal("1000"), null)).isTrue();
        assertThat(ReglasCriterio.cumple(c, null, new BigDecimal("999.99"), null)).isFalse();
        assertThatThrownBy(() -> crear("Otro", Indicador.VALOR, "999", "1000")).hasMessageContaining("superpone");
        service.crear(new SolicitudCriterio("Bajo", "", Indicador.VALOR, Condicion.MENOR_IGUAL,
                null, new BigDecimal("999.99"), null, null), "admin");
    }

    @Test void igualdadSeNormalizaYUnClienteSinComprasPuedeCumplirCero() {
        var c = service.crear(new SolicitudCriterio("Sin compras", "", Indicador.FRECUENCIA, Condicion.IGUAL,
                BigDecimal.ZERO, null, null, null), "admin");
        assertThat(c.maximo()).isEqualByComparingTo("0");
        assertThat(ReglasCriterio.cumple(c, BigDecimal.ZERO, null, List.of())).isTrue();
        assertThat(ReglasCriterio.cumple(c, BigDecimal.ONE, null, List.of())).isFalse();
    }

    @Test void categoriasNormalizadasNoSeDuplicanYSeEvaluaNombreCompleto() {
        var c = service.crear(categoria("Tecnologia", "Electrónica"), "admin");
        assertThatThrownBy(() -> service.crear(categoria("Repetida", "  ELECTRONICA  "), "admin")).hasMessageContaining("superpone");
        assertThat(ReglasCriterio.cumple(c, null, null, List.of("libros", "electronica"))).isTrue();
        assertThat(ReglasCriterio.cumple(c, null, null, List.of("electronica infantil"))).isFalse();
        assertThat(ReglasCriterio.cumple(c, null, null, List.of())).isFalse();
    }

    @Test void validaNombresVaciosRangosInvertidosNegativosYDecimalesEnFrecuencia() throws Exception {
        for (String cuerpo : List.of(
                "{\"nombre\":\" \",\"indicador\":\"FRECUENCIA\",\"condicion\":\"ENTRE\",\"minimo\":1,\"maximo\":3}",
                "{\"nombre\":\"Caso\",\"indicador\":\"FRECUENCIA\",\"condicion\":\"ENTRE\",\"minimo\":9,\"maximo\":3}",
                "{\"nombre\":\"Caso\",\"indicador\":\"FRECUENCIA\",\"condicion\":\"ENTRE\",\"minimo\":-1,\"maximo\":3}",
                "{\"nombre\":\"Caso\",\"indicador\":\"FRECUENCIA\",\"condicion\":\"ENTRE\",\"minimo\":1.5,\"maximo\":3}",
                "{\"nombre\":\"Caso\",\"indicador\":\"VALOR\",\"condicion\":\"ENTRE\",\"minimo\":1.001,\"maximo\":3}",
                "{\"nombre\":\"Caso\",\"indicador\":\"CATEGORIA\",\"condicion\":\"CONTIENE\",\"categoria\":\" \"}",
                "{\"nombre\":\"Caso\",\"indicador\":\"FRECUENCIA\",\"condicion\":\"ENTRE\",\"minimo\":1}",
                "{\"nombre\":\"Caso\",\"indicador\":\"OTRO\",\"condicion\":\"ENTRE\"}")) {
            mvc.perform(admin(post("/api/segmentacion/criterios")).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").isNotEmpty());
        }
        assertThat(service.listar(null, null)).isEmpty();
    }

    @Test void nombresSonUnicosIgnorandoMayusculasTildesYEspacios() {
        crear("Frecuéncia alta", Indicador.FRECUENCIA, "10", "20");
        assertThatThrownBy(() -> crear("  FRECUENCIA   ALTA ", Indicador.VALOR, "0", "500"))
                .hasMessageContaining("nombre");
    }

    @Test void edicionDesactivacionYReactivacionConservanFotosUsuarioYFecha() {
        var original = crear("Habituales", Indicador.FRECUENCIA, "5", "9");
        var editado = service.editar(original.id(), new SolicitudCriterio("Habituales actualizados", "Cambio de umbral",
                Indicador.FRECUENCIA, Condicion.ENTRE, new BigDecimal("4"), new BigDecimal("9"), null, original.version()), "frederick");
        var inactivo = service.cambiarEstado(editado.id(), false, editado.version(), "frederick");
        assertThat(service.listar(true, null)).isEmpty();
        assertThat(ReglasCriterio.cumple(inactivo, new BigDecimal("5"), null, null)).isFalse();
        service.cambiarEstado(inactivo.id(), true, inactivo.version(), "admin");
        var historial = service.historial(original.id());
        assertThat(historial).hasSize(4);
        var edicion = historial.get(2);
        assertThat(edicion.usuario()).isEqualTo("frederick");
        assertThat(edicion.fecha()).isNotNull();
        assertThat(edicion.anterior().get("nombre").asText()).isEqualTo("Habituales");
        assertThat(edicion.nuevo().get("minimo").decimalValue()).isEqualByComparingTo("4");
        assertThat(historial.get(1).nuevo().get("activo").asBoolean()).isFalse();
    }

    @Test void editarYReactivarTambienValidanSuperposiciones() {
        var a = crear("Primero", Indicador.FRECUENCIA, "1", "3");
        var b = crear("Segundo", Indicador.FRECUENCIA, "4", "9");
        assertThatThrownBy(() -> service.editar(b.id(), new SolicitudCriterio(b.nombre(), "", b.indicador(), b.condicion(),
                BigDecimal.ONE, BigDecimal.TEN, null, b.version()), "admin")).hasMessageContaining("superpone");
        a = service.cambiarEstado(a.id(), false, a.version(), "admin");
        crear("Sustituto", Indicador.FRECUENCIA, "1", "3");
        var inactivo = a;
        assertThatThrownBy(() -> service.cambiarEstado(inactivo.id(), true, inactivo.version(), "admin")).hasMessageContaining("superpone");
        assertThat(service.historial(inactivo.id())).hasSize(2);
    }

    @Test void cambiosConVersionObsoletaNoSobrescribenNiGeneranAuditoria() {
        var c = crear("Criterio", Indicador.VALOR, "0", "100");
        service.cambiarEstado(c.id(), false, c.version(), "admin");
        assertThatThrownBy(() -> service.editar(c.id(), new SolicitudCriterio("Viejo", "", c.indicador(), c.condicion(),
                c.minimo(), c.maximo(), null, c.version()), "otro")).hasMessageContaining("Otro usuario");
        assertThat(service.historial(c.id())).hasSize(2);
    }

    @Test void auditoriaNoPermiteActualizarBorrarNiTruncar() {
        var c = crear("Auditado", Indicador.FRECUENCIA, "1", "2");
        long id = service.historial(c.id()).get(0).id();
        for (String sql : List.of("UPDATE auditoria.evento SET usuario='otro' WHERE id=" + id,
                "DELETE FROM auditoria.evento WHERE id=" + id, "TRUNCATE auditoria.evento")) {
            assertThatThrownBy(() -> jdbc.execute(sql)).hasMessageContaining("solo lectura");
        }
        assertThat(service.historial(c.id())).hasSize(1);
    }

    @Test void unFalloDeAuditoriaRevierteElCriterio() {
        jdbc.execute("""
            CREATE FUNCTION auditoria.fallo_prueba() RETURNS trigger AS $$ BEGIN
              IF NEW.usuario = 'fallar-auditoria' THEN RAISE EXCEPTION 'fallo de prueba'; END IF;
              RETURN NEW; END; $$ LANGUAGE plpgsql;
            CREATE TRIGGER fallo_prueba BEFORE INSERT ON auditoria.evento FOR EACH ROW EXECUTE FUNCTION auditoria.fallo_prueba();
            """);
        try {
            assertThatThrownBy(() -> service.crear(categoria("Revertido", "Libros"), "fallar-auditoria"))
                    .hasMessageContaining("fallo de prueba");
            assertThat(service.listar(null, null)).isEmpty();
        } finally { jdbc.execute("DROP TRIGGER fallo_prueba ON auditoria.evento; DROP FUNCTION auditoria.fallo_prueba()"); }
    }

    @Test void dosAltasSimultaneasNoPuedenCrearIntervalosSuperpuestos() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var listos = new CountDownLatch(2);
        var salida = new CountDownLatch(1);
        try {
            var tareas = java.util.stream.IntStream.range(0, 2).mapToObj(n -> executor.submit(() -> {
                listos.countDown(); salida.await(10, TimeUnit.SECONDS);
                try { crear("Concurrente " + n, Indicador.FRECUENCIA, "1", "10"); return 201; }
                catch (ResponseStatusException e) { return e.getStatusCode().value(); }
            })).toList();
            assertThat(listos.await(10, TimeUnit.SECONDS)).isTrue(); salida.countDown();
            assertThat(List.of(tareas.get(0).get(10, TimeUnit.SECONDS), tareas.get(1).get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(service.listar(null, null)).hasSize(1);
        } finally { salida.countDown(); executor.shutdownNow(); }
    }

    @Test void identidadAusenteORolNoAdministradorNoGestionaCriterios() throws Exception {
        mvc.perform(get("/api/segmentacion/criterios")).andExpect(status().isUnauthorized());
        for (String rol : List.of("AGENTE_ATENCION", "GERENTE_COMERCIAL")) {
            mvc.perform(post("/api/segmentacion/criterios").header("X-Usuario", "otro").header("X-Usuario-Rol", rol)
                    .header("X-Usuario-Permisos", "SEGMENTACION_CONFIGURAR").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(categoria("Prohibido", "Ropa")))).andExpect(status().isForbidden());
        }
        assertThat(service.listar(null, null)).isEmpty();
    }

    @Test void apiCompruebaUnaReglaPersistidaYValidaElDatoDelCaso() throws Exception {
        var c = crear("Prueba", Indicador.VALOR, "100.01", "500");
        mvc.perform(admin(post("/api/segmentacion/criterios/" + c.id() + "/comprobar"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":100.01}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cumple").value(true));
        mvc.perform(admin(post("/api/segmentacion/criterios/" + c.id() + "/comprobar"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    private Criterio crear(String nombre, Indicador indicador, String min, String max) {
        return service.crear(new SolicitudCriterio(nombre, "", indicador, Condicion.ENTRE,
                new BigDecimal(min), new BigDecimal(max), null, null), "admin");
    }
    private SolicitudCriterio categoria(String nombre, String categoria) {
        return new SolicitudCriterio(nombre, "", Indicador.CATEGORIA, Condicion.CONTIENE, null, null, categoria, null);
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder admin(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req) {
        return req.header("X-Usuario", "admin").header("X-Usuario-Rol", "ADMINISTRADOR_CRM")
                .header("X-Usuario-Permisos", "SEGMENTACION_CONFIGURAR");
    }
}
