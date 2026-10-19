package com.maxiconecta.crm.comportamiento.sugerencia;

import com.maxiconecta.crm.comportamiento.compra.Identificador;
import com.maxiconecta.crm.comportamiento.preferencia.IdentificadoresPreferencias;
import com.maxiconecta.crm.comportamiento.preferencia.PerfilesPreferencias;
import com.maxiconecta.crm.comportamiento.preferencia.PreferenciasException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-384: sugerencia de categorías afines con segmentos de prueba sobre PostgreSQL real. Los
 * perfiles se construyen con el flujo real de SCRUM-41; Perfil y Segmentación se simulan.
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SugerenciasIntegracionTest {
    private static final String RUTA = "/api/comportamiento/clientes/42/sugerencias-categorias";
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
    @MockBean IdentificadoresPreferencias identificadores;
    @MockBean MiembrosSegmento segmentos;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PerfilesPreferencias perfiles;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE comportamiento.perfil_preferencias, comportamiento.evento_recibido CASCADE");
        // El cliente N del CRM compra con el identificador de origen CLI-N.
        when(identificadores.obtener(anyLong())).thenAnswer(i -> List.of(new Identificador("CLI-" + i.getArgument(0))));
        segmento("Frecuentes", 7, 15, 23, 31);
    }

    @Test
    void sugiereLasCategoriasDelSegmentoQueElClienteNoCompraOrdenadasPorAfinidad() throws Exception {
        segmentoDePrueba();
        mvc.perform(get(RUTA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(42))
                .andExpect(jsonPath("$.segmento").value("Frecuentes"))
                .andExpect(jsonPath("$.criterio").value(MotorSugerencias.CRITERIO))
                .andExpect(jsonPath("$.clientesDelSegmento").value(4))
                .andExpect(jsonPath("$.sinSugerencias").value(false))
                .andExpect(jsonPath("$.sugerencias[*].categoria", contains("Electrónica", "Deportes")))
                .andExpect(jsonPath("$.sugerencias[0].afinidad").value(0.75))
                .andExpect(jsonPath("$.sugerencias[0].clientesQueCompran").value(3))
                .andExpect(jsonPath("$.sugerencias[0].montoSegmento").value(900))
                .andExpect(jsonPath("$.sugerencias[1].afinidad").value(0.5));
    }

    @Test
    void unaCompraNuevaDelClienteQuitaLaCategoriaDeLasSugerencias() throws Exception {
        segmentoDePrueba();
        compra(42, "Electrónica", "120");
        perfiles.actualizarPorEvento(42, "scrum44-compra", "COMPRA_CONFIRMADA");

        mvc.perform(get(RUTA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sugerencias[*].categoria", contains("Deportes")));
    }

    @Test
    void consultarNoModificaElPerfilDelCliente() throws Exception {
        segmentoDePrueba();
        long version = perfiles.consultar(42).version();
        mvc.perform(get(RUTA)).andExpect(status().isOk());
        mvc.perform(get(RUTA)).andExpect(status().isOk());
        assertThat(perfiles.consultar(42).version()).isEqualTo(version);
    }

    @Test
    void clienteSinPerfilNoRecibeSugerenciasYSeInformaElMotivo() throws Exception {
        segmentoDePrueba();
        jdbc.update("DELETE FROM comportamiento.perfil_preferencias WHERE cliente_id = 42");
        sinSugerencias("SIN_PERFIL");
    }

    @Test
    void perfilSinCategoriasNoRecibeSugerencias() throws Exception {
        segmentoDePrueba();
        jdbc.update("DELETE FROM comportamiento.compra_item WHERE id_compra IN "
                + "(SELECT id FROM comportamiento.compra WHERE id_cliente_origen = 'CLI-42')");
        jdbc.update("DELETE FROM comportamiento.compra WHERE id_cliente_origen = 'CLI-42'");
        perfiles.construir(42);
        sinSugerencias("SIN_PREFERENCIAS");
    }

    @Test
    void clienteSinSegmentoNoRecibeSugerenciasYSeInformaElMotivo() throws Exception {
        segmentoDePrueba();
        when(segmentos.obtener(42)).thenReturn(new MiembrosSegmento.Segmento(null, List.of()));
        sinSugerencias("SIN_SEGMENTO");
    }

    @Test
    void segmentoSinOtrosClientesConPreferencias() throws Exception {
        clienteConPerfil(42, "Hogar", "100");
        segmento("Nuevos", 7, 15);
        sinSugerencias("SIN_DATOS_SEGMENTO");
    }

    @Test
    void segmentoQueSoloCompraLoQueElClienteYaCompra() throws Exception {
        clienteConPerfil(42, "Hogar", "100", "Electrónica", "50");
        clienteConPerfil(7, "Hogar", "80");
        clienteConPerfil(15, "Electrónica", "60", "Juguetes", "30");
        segmento("Frecuentes", 7, 15);
        mvc.perform(get(RUTA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.motivo").value("SIN_CATEGORIAS_NUEVAS"))
                .andExpect(jsonPath("$.clientesDelSegmento").value(2))
                .andExpect(jsonPath("$.sugerencias").isEmpty());
    }

    @Test
    void erroresDePerfilYSegmentacionNoSeConfundenConSinSugerencias() throws Exception {
        segmentoDePrueba();
        for (HttpStatus estado : List.of(HttpStatus.NOT_FOUND, HttpStatus.CONFLICT, HttpStatus.SERVICE_UNAVAILABLE)) {
            doThrow(new PreferenciasException(estado, "Identidad no disponible")).when(identificadores).obtener(42);
            mvc.perform(get(RUTA)).andExpect(status().is(estado.value()))
                    .andExpect(jsonPath("$.mensaje").value("Identidad no disponible"));
        }
        doReturn(List.of(new Identificador("CLI-42"))).when(identificadores).obtener(42);
        doThrow(new PreferenciasException(HttpStatus.SERVICE_UNAVAILABLE, "Segmentación no disponible"))
                .when(segmentos).obtener(42);
        mvc.perform(get(RUTA)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value("Segmentación no disponible"));
    }

    /** Ana (42) compra Hogar; sus 4 compañeros: Hogar 4, Electrónica 3, Deportes 2, Juguetes 1 (ejemplo del documento). */
    private void segmentoDePrueba() {
        clienteConPerfil(42, "Hogar", "100");
        clienteConPerfil(7, "Hogar", "50", "Electrónica", "300", "Deportes", "40");
        clienteConPerfil(15, "Hogar", "60", "Electrónica", "400", "Juguetes", "20");
        clienteConPerfil(23, "Hogar", "70", "Electrónica", "200", "Deportes", "60");
        clienteConPerfil(31, "Hogar", "90");
    }

    private void sinSugerencias(String motivo) throws Exception {
        mvc.perform(get(RUTA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sinSugerencias").value(true))
                .andExpect(jsonPath("$.sugerencias").isEmpty())
                .andExpect(jsonPath("$.motivo").value(motivo))
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
    }

    private void segmento(String nombre, long... companeros) {
        when(segmentos.obtener(42)).thenReturn(new MiembrosSegmento.Segmento(nombre,
                java.util.Arrays.stream(companeros).boxed().toList()));
    }

    private void clienteConPerfil(long cliente, String... categoriasYMontos) {
        compra(cliente, categoriasYMontos);
        perfiles.construir(cliente);
    }

    private void compra(long cliente, String... categoriasYMontos) {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 1; i < categoriasYMontos.length; i += 2) { total = total.add(new BigDecimal(categoriasYMontos[i])); }
        long evento = jdbc.queryForObject(
                "INSERT INTO comportamiento.evento_recibido (estado, contenido) VALUES ('PROCESADO', '{}') RETURNING id", Long.class);
        long id = jdbc.queryForObject("""
                INSERT INTO comportamiento.compra (id_compra_origen, id_cliente_origen, fecha, monto_total, id_evento)
                VALUES (?, ?, '2026-09-01T00:00:00Z', ?, ?) RETURNING id
                """, Long.class, UUID.randomUUID().toString(), "CLI-" + cliente, total, evento);
        for (int i = 0; i < categoriasYMontos.length; i += 2) {
            jdbc.update("INSERT INTO comportamiento.compra_item (id_compra, categoria, cantidad, monto) VALUES (?, ?, 1, ?)",
                    id, categoriasYMontos[i], new BigDecimal(categoriasYMontos[i + 1]));
        }
    }
}
