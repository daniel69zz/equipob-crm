package com.maxiconecta.crm.comportamiento.preferencia;

import com.maxiconecta.crm.comportamiento.compra.Identificador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** SCRUM-364: API, Flyway, consultas JPA y persistencia reales sobre PostgreSQL temporal. */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PreferenciasIntegracionTest {
    private static final String RUTA = "/api/comportamiento/clientes/42/preferencias";
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
    @MockBean IdentificadoresPreferencias identificadores;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PerfilesPreferencias servicio;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE comportamiento.perfil_preferencias, comportamiento.evento_recibido CASCADE");
        when(identificadores.obtener(42)).thenReturn(List.of(new Identificador("CLI-A"), new Identificador("CLI-B")));
        when(identificadores.obtener(99)).thenReturn(List.of(new Identificador("OTRO")));
    }

    @Test
    void construyeOrdenadoConTodosLosIdentificadoresYPersisteLaMismaRespuesta() throws Exception {
        compra("CLI-A", "Hogar", "60", "Deportes", "40");
        compra("CLI-B", "Hogar", "60");
        compra("CLI-A", "Electrónica", "300");
        compra("OTRO", "Ajena", "900");
        mvc.perform(post(RUTA + "/construir").param("identificador", "OTRO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clienteId").value(42))
                .andExpect(jsonPath("$.construido").value(true)).andExpect(jsonPath("$.sinPreferencias").value(false))
                .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.construidoEn").isNotEmpty())
                .andExpect(jsonPath("$.categorias[*].categoria", contains("Electrónica", "Hogar", "Deportes")))
                .andExpect(jsonPath("$.categorias[1].compras").value(2))
                .andExpect(jsonPath("$.categorias[1].monto").value(120));
        mvc.perform(get(RUTA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.categorias[*].posicion", contains(1, 2, 3)))
                .andExpect(jsonPath("$.categorias[0].monto").value(300));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comportamiento.preferencia_categoria WHERE cliente_id=42", Long.class)).isEqualTo(3);
    }

    @Test
    void sinComprasDistinguePendienteDeConstruidoVacio() throws Exception {
        mvc.perform(get(RUTA)).andExpect(status().isOk()).andExpect(jsonPath("$.construido").value(false))
                .andExpect(jsonPath("$.version").value(0));
        mvc.perform(post(RUTA + "/construir")).andExpect(status().isOk())
                .andExpect(jsonPath("$.construido").value(true)).andExpect(jsonPath("$.sinPreferencias").value(true))
                .andExpect(jsonPath("$.categorias").isEmpty());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comportamiento.perfil_preferencias", Long.class)).isEqualTo(1);
        mvc.perform(get(RUTA)).andExpect(jsonPath("$.construido").value(true));
    }

    @Test
    void unaSolaCategoriaQuedaPrimeraIncluyendoComprasAntiguas() throws Exception {
        long id = compra("CLI-A", "Alimentos", "50");
        jdbc.update("UPDATE comportamiento.compra SET fecha='2000-01-01T00:00:00Z' WHERE id=?", id);
        mvc.perform(post(RUTA + "/construir")).andExpect(status().isOk())
                .andExpect(jsonPath("$.categorias.length()").value(1))
                .andExpect(jsonPath("$.categorias[0].categoria").value("Alimentos"))
                .andExpect(jsonPath("$.categorias[0].posicion").value(1));
    }

    @Test
    void anularTodoDejaUnPerfilVacioYEliminaLasPreferenciasAnteriores() throws Exception {
        long id = compra("CLI-A", "Hogar", "100");
        servicio.construir(42);
        jdbc.update("UPDATE comportamiento.compra SET monto_revertido=monto_total, estado='ANULADA' WHERE id=?", id);
        mvc.perform(post(RUTA + "/construir")).andExpect(status().isOk())
                .andExpect(jsonPath("$.sinPreferencias").value(true)).andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.categorias").isEmpty());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comportamiento.preferencia_categoria", Long.class)).isZero();
    }

    @Test
    void descuentaLaDevolucionPorCategoriaYExcluyeLaCategoriaTotalmenteDevuelta() throws Exception {
        long id = compra("CLI-A", "Hogar", "100", "Deportes", "80");
        devolver(id, "Hogar", "100");
        mvc.perform(post(RUTA + "/construir")).andExpect(status().isOk())
                .andExpect(jsonPath("$.categorias[*].categoria", contains("Deportes")))
                .andExpect(jsonPath("$.categorias[0].monto").value(80));
    }

    @Test
    void reconstruirNoDuplicaFilasYConsultarNoRecalcula() throws Exception {
        compra("CLI-A", "Hogar", "100");
        servicio.construir(42);
        compra("CLI-A", "Deportes", "200");
        mvc.perform(get(RUTA)).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.categorias[*].categoria", contains("Hogar")));
        servicio.construir(42);
        servicio.construir(42);
        mvc.perform(get(RUTA)).andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.categorias[*].categoria", contains("Deportes", "Hogar")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comportamiento.preferencia_categoria", Long.class)).isEqualTo(2);
    }

    @Test
    void clienteSinIdentificadoresNoRecibeComprasDeOtros() throws Exception {
        compra("OTRO", "Deportes", "200");
        when(identificadores.obtener(42)).thenReturn(List.of());
        mvc.perform(post(RUTA + "/construir")).andExpect(status().isOk()).andExpect(jsonPath("$.categorias").isEmpty());
    }

    @Test
    void unErrorDePerfilDevuelve503YPreservaElResultadoAnterior() throws Exception {
        compra("CLI-A", "Hogar", "100");
        servicio.construir(42);
        when(identificadores.obtener(42)).thenThrow(new PreferenciasException(HttpStatus.SERVICE_UNAVAILABLE, "Perfil no disponible"));
        mvc.perform(post(RUTA + "/construir")).andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT version FROM comportamiento.perfil_preferencias WHERE cliente_id=42", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT monto FROM comportamiento.preferencia_categoria WHERE cliente_id=42", BigDecimal.class))
                .isEqualByComparingTo("100");
    }

    @Test
    void rechazaDatosInconsistentesYConservaLaVersionAnterior() throws Exception {
        long id = compra("CLI-A", "Hogar", "100");
        servicio.construir(42);
        jdbc.update("UPDATE comportamiento.compra_item SET monto=50 WHERE id_compra=?", id);
        mvc.perform(post(RUTA + "/construir")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("inconsistentes")));
        assertThat(jdbc.queryForObject("SELECT version FROM comportamiento.perfil_preferencias WHERE cliente_id=42", Long.class)).isEqualTo(1);
    }

    @Test
    void unFalloAlInsertarCategoriasRevierteCabeceraYBorrado() {
        compra("CLI-A", "Hogar", "100");
        PerfilPreferencias anterior = servicio.construir(42);
        compra("CLI-A", "Deportes", "200");
        jdbc.execute("ALTER TABLE comportamiento.preferencia_categoria ADD CONSTRAINT fallo_prueba CHECK (categoria <> 'Deportes')");
        try {
            assertThatThrownBy(() -> servicio.construir(42)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(servicio.consultar(42)).isEqualTo(anterior);
        } finally {
            jdbc.execute("ALTER TABLE comportamiento.preferencia_categoria DROP CONSTRAINT fallo_prueba");
        }
    }

    @Test
    void dosConstruccionesConcurrentesNoMezclanNiDuplicanCategorias() throws Exception {
        compra("CLI-A", "Hogar", "100", "Deportes", "200");
        var executor = Executors.newFixedThreadPool(2);
        var inicio = new CountDownLatch(1);
        try {
            var primera = executor.submit(() -> { inicio.await(); return servicio.construir(42); });
            var segunda = executor.submit(() -> { inicio.await(); return servicio.construir(42); });
            inicio.countDown();
            var a = primera.get(20, TimeUnit.SECONDS);
            var b = segunda.get(20, TimeUnit.SECONDS);
            assertThat(List.of(a.version(), b.version())).containsExactlyInAnyOrder(1L, 2L);
            assertThat(a.categorias()).isEqualTo(b.categorias());
            assertThat(servicio.consultar(42).version()).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM comportamiento.preferencia_categoria", Long.class)).isEqualTo(2);
        } finally { executor.shutdownNow(); }
    }

    @Test
    void clientesDistintosMantienenPerfilesIndependientes() {
        compra("CLI-A", "Hogar", "100");
        compra("OTRO", "Deportes", "200");
        servicio.construir(42);
        servicio.construir(99);
        assertThat(servicio.consultar(42).categorias()).extracting(c -> c.categoria()).containsExactly("Hogar");
        assertThat(servicio.consultar(99).categorias()).extracting(c -> c.categoria()).containsExactly("Deportes");
    }

    @Test
    void unClienteInexistenteResponde404YSinCrearCabecera() throws Exception {
        when(identificadores.obtener(404)).thenThrow(new PreferenciasException(HttpStatus.NOT_FOUND, "No existe el cliente 404"));
        mvc.perform(post("/api/comportamiento/clientes/404/preferencias/construir")).andExpect(status().isNotFound());
        mvc.perform(get("/api/comportamiento/clientes/no-es-id/preferencias")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comportamiento.perfil_preferencias", Long.class)).isZero();
    }

    @Test
    void datosLegadosSinCategoriaSeExcluyenEnLaConstruccionCompleta() throws Exception {
        // Solo en este PostgreSQL temporal: simula datos legados que la ingesta actual ya rechaza.
        jdbc.execute("ALTER TABLE comportamiento.compra_item DROP CONSTRAINT compra_item_categoria_no_vacia");
        jdbc.execute("ALTER TABLE comportamiento.compra_item ALTER COLUMN categoria DROP NOT NULL");
        try {
            compra("CLI-A", null, "500", " ", "200", "Hogar", "50");
            mvc.perform(post(RUTA + "/construir")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.categorias[*].categoria", contains("Hogar")))
                    .andExpect(jsonPath("$.categorias[0].monto").value(50));
        } finally {
            jdbc.update("DELETE FROM comportamiento.compra_item WHERE categoria IS NULL OR trim(categoria)=''");
            jdbc.execute("ALTER TABLE comportamiento.compra_item ALTER COLUMN categoria SET NOT NULL");
            jdbc.execute("ALTER TABLE comportamiento.compra_item ADD CONSTRAINT compra_item_categoria_no_vacia CHECK (categoria ~ '[^[:space:]]')");
        }
    }

    private long evento() {
        return jdbc.queryForObject("INSERT INTO comportamiento.evento_recibido (estado, contenido) VALUES ('PROCESADO', '{}') RETURNING id", Long.class);
    }

    private long compra(String cliente, String... categoriasYMontos) {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 1; i < categoriasYMontos.length; i += 2) { total = total.add(new BigDecimal(categoriasYMontos[i])); }
        long id = jdbc.queryForObject("""
                INSERT INTO comportamiento.compra (id_compra_origen, id_cliente_origen, fecha, monto_total, id_evento)
                VALUES (?, ?, '2026-09-01T00:00:00Z', ?, ?) RETURNING id
                """, Long.class, UUID.randomUUID().toString(), cliente, total, evento());
        for (int i = 0; i < categoriasYMontos.length; i += 2) {
            jdbc.update("INSERT INTO comportamiento.compra_item (id_compra, categoria, cantidad, monto) VALUES (?, ?, 1, ?)",
                    id, categoriasYMontos[i], new BigDecimal(categoriasYMontos[i + 1]));
        }
        return id;
    }

    private void devolver(long compra, String categoria, String monto) {
        long anulacion = jdbc.queryForObject("""
                INSERT INTO comportamiento.anulacion (id_anulacion_origen, id_compra, tipo, fecha, monto_revertido, motivo, id_evento)
                VALUES (?, ?, 'PARCIAL', now(), ?, 'Prueba SCRUM-41', ?) RETURNING id
                """, Long.class, UUID.randomUUID().toString(), compra, new BigDecimal(monto), evento());
        jdbc.update("INSERT INTO comportamiento.anulacion_item (id_anulacion, categoria, cantidad, monto) VALUES (?, ?, 1, ?)",
                anulacion, categoria, new BigDecimal(monto));
        jdbc.update("UPDATE comportamiento.compra SET monto_revertido=?, estado='DEVOLUCION_PARCIAL' WHERE id=?", new BigDecimal(monto), compra);
    }
}
