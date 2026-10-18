package com.maxiconecta.crm.segmentacion.consulta;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.util.HashSet;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Sql("/scrum28-datos.sql")
class ConsultaClientesIntegracionTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void base(DynamicPropertyRegistry r) {
        for (String[] servicio : new String[][]{
                {"api-gateway", "seguridad"}, {"servicio-perfil", "perfil"}, {"servicio-comportamiento", "comportamiento"}}) {
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .schemas(servicio[1]).locations("filesystem:../" + servicio[0] + "/src/main/resources/db/migration")
                    .load().migrate();
        }
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired RepositorioConsultaClientes consulta;
    @MockBean UmbralesConsumo umbrales;
    private final UmbralesConsumo.Valores valores = new UmbralesConsumo.Valores(new BigDecimal("500"), new BigDecimal("5000"));
    @BeforeEach void preparar() { when(umbrales.obtener()).thenReturn(valores); }

    @Test void listaConCuatroColumnasYTotalPorClienteCrm() throws Exception {
        mvc.perform(permitido(get("/api/segmentacion/clientes").param("segmentoId", "28001")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(128))
                .andExpect(jsonPath("$.clientes.length()").value(20))
                .andExpect(jsonPath("$.clientes[0].nombre").value("Ana Unificada"))
                .andExpect(jsonPath("$.clientes[0].nivelConsumo").value("MEDIO"))
                .andExpect(jsonPath("$.clientes[0].segmento").value("Frecuentes SCRUM-28"))
                .andExpect(jsonPath("$.clientes[0].ultimaCompra").value("2026-10-11T12:00:00Z"));
    }

    @Test void elCatalogoIncluyeVaciosInactivosYAnteriores() throws Exception {
        jdbc.update("UPDATE segmentacion.criterio SET activo = false WHERE id = 28002");
        mvc.perform(permitido(get("/api/segmentacion/segmentos"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        mvc.perform(permitido(get("/api/segmentacion/clientes").param("segmentoId", "28002")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(permitido(get("/api/segmentacion/clientes").param("segmento", "Segmento anterior SCRUM-28")))
                .andExpect(jsonPath("$.clientes[0].nombre").value("Elena Segmento anterior"));
    }

    @Test void combinaFiltrosAntesDePaginarYSumaLasCuentasUnificadas() throws Exception {
        mvc.perform(permitido(get("/api/segmentacion/clientes").param("segmentoId", "28001")
                        .param("nivelConsumo", "MEDIO").param("nombre", "ana")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.clientes[0].clienteId").value(280001));
        mvc.perform(permitido(get("/api/segmentacion/clientes").param("nivelConsumo", "ALTO")))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.clientes[0].nombre").value("Elena Segmento anterior"));
    }

    @Test void sinComprasYAnulacionesQuedanSinNivelNiFecha() throws Exception {
        mvc.perform(permitido(get("/api/segmentacion/clientes").param("segmentoId", "28001").param("nivelConsumo", "SIN_NIVEL")))
                .andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.clientes[0].nivelConsumo").isEmpty())
                .andExpect(jsonPath("$.clientes[1].ultimaCompra").isEmpty());
        jdbc.update("UPDATE comportamiento.compra SET monto_revertido = monto_total, estado = 'ANULADA' WHERE id_cliente_origen LIKE 'S28-ANA-%'");
        mvc.perform(permitido(get("/api/segmentacion/clientes").param("nivelConsumo", "SIN_NIVEL").param("nombre", "Ana")))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.clientes[0].ultimaCompra").isEmpty());
    }

    @Test void consultaSegmentoPorIdAunqueSeRenombre() throws Exception {
        jdbc.update("UPDATE segmentacion.criterio SET nombre = 'Nombre nuevo' WHERE id = 28001");
        mvc.perform(permitido(get("/api/segmentacion/clientes").param("segmentoId", "28001")))
                .andExpect(jsonPath("$.total").value(128))
                .andExpect(jsonPath("$.clientes[0].segmento").value("Nombre nuevo"));
    }

    @Test void paginasEstablesSinRepeticionesNiPerfilesAbsorbidos() {
        var ids = new HashSet<Long>();
        for (int pagina = 0; pagina < 7; pagina++) {
            var r = consulta.buscar(new FiltroConsultaClientes(28001L, null, null, null, pagina, 20, "nombre", "asc"), valores);
            assertThat(r.total()).isEqualTo(128);
            r.clientes().forEach(c -> assertThat(ids.add(c.clienteId())).isTrue());
        }
        assertThat(ids).hasSize(128).doesNotContain(280004L);
    }

    @Test void ordenaGlobalmenteYLaPaginaFueraDeRangoMantieneTotal() throws Exception {
        mvc.perform(permitido(get("/api/segmentacion/clientes").param("segmentoId", "28001")
                        .param("orden", "nombre").param("direccion", "desc").param("tamanio", "10")))
                .andExpect(jsonPath("$.clientes[0].nombre").value("Cliente 125 Paginación"));
        mvc.perform(permitido(get("/api/segmentacion/clientes").param("segmentoId", "28001").param("pagina", "500")))
                .andExpect(jsonPath("$.clientes.length()").value(0)).andExpect(jsonPath("$.total").value(128));
    }

    @Test void aplicaUmbralesVigentesSinHardcode() throws Exception {
        when(umbrales.obtener()).thenReturn(new UmbralesConsumo.Valores(new BigDecimal("600"), new BigDecimal("1000")));
        mvc.perform(permitido(get("/api/segmentacion/clientes").param("nivelConsumo", "ALTO")))
                .andExpect(jsonPath("$.total").value(126));
        mvc.perform(permitido(get("/api/segmentacion/clientes").param("nivelConsumo", "MEDIO")))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test void diferenciaErroresDeListasVacias() throws Exception {
        mvc.perform(permitido(get("/api/segmentacion/clientes").param("segmentoId", "999"))).andExpect(status().isNotFound());
        when(umbrales.obtener()).thenThrow(new ResponseStatusException(SERVICE_UNAVAILABLE, "Comportamiento no disponible"));
        mvc.perform(permitido(get("/api/segmentacion/clientes").param("segmentoId", "28001"))).andExpect(status().isServiceUnavailable());
    }

    @Test void validaFiltrosYTamanioAntesDeConsultar() throws Exception {
        mvc.perform(permitido(get("/api/segmentacion/clientes"))).andExpect(status().isBadRequest());
        for (String[] caso : new String[][]{{"pagina", "-1"}, {"tamanio", "101"}, {"tamanio", "0"},
                {"orden", "nombre;DROP TABLE"}, {"direccion", "random"}, {"nivelConsumo", "VIP"}}) {
            mvc.perform(permitido(get("/api/segmentacion/clientes").param("segmentoId", "28001").param(caso[0], caso[1])))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(permitido(get("/api/segmentacion/clientes").param("segmentoId", "28001").param("nombre", "%")))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test void exigeIdentidadYPermisoTambienEnElServicio() throws Exception {
        for (String ruta : new String[]{"/api/segmentacion/clientes", "/api/segmentacion/segmentos"}) {
            mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
            mvc.perform(get(ruta).header("X-Usuario", "agente").header("X-Usuario-Permisos", "CLIENTE_CONSULTAR"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test void diezMilClientesSeDevuelvenEnPaginasLimitadas() {
        jdbc.update("""
                INSERT INTO perfil.cliente(id, nombres, estado)
                SELECT 900000 + n, 'Carga ' || lpad(n::text, 5, '0'), 'COMPLETO'
                FROM generate_series(1, 10000) n
                """);
        jdbc.update("""
                INSERT INTO segmentacion.segmento_cliente(cliente_id, segmento, criterio_id)
                SELECT 900000 + n, 'Frecuentes SCRUM-28', 28001 FROM generate_series(1, 10000) n
                """);
        var r = consulta.buscar(new FiltroConsultaClientes(28001L, null, "SIN_NIVEL", null,
                99, 100, "nombre", "asc"), valores);
        assertThat(r.total()).isEqualTo(10002);
        assertThat(r.clientes()).hasSize(100);
        var ultima = consulta.buscar(new FiltroConsultaClientes(28001L, null, "SIN_NIVEL", null,
                100, 100, "nombre", "asc"), valores);
        assertThat(ultima.clientes()).hasSize(2);
    }

    private MockHttpServletRequestBuilder permitido(MockHttpServletRequestBuilder req) {
        return req.header("X-Usuario", "analista").header("X-Usuario-Permisos", "SEGMENTOS_CONSULTAR");
    }
}
