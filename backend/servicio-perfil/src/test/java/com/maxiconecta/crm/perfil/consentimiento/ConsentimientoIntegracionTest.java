package com.maxiconecta.crm.perfil.consentimiento;

import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-556 · Registro, revocación y vigencia del consentimiento a través del endpoint de gestión
 * (docs/perfil/consentimiento-datos.md).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ConsentimientoIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    private static final String RUTA = "/api/perfil/clientes/{id}/consentimiento";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ClienteRepository clientes;

    @Autowired
    private GestionConsentimiento gestion;

    @Autowired
    private JdbcTemplate jdbc;

    private final LocalDate hoy = LocalDate.now();

    @BeforeEach
    void limpiarBase() {
        jdbc.execute("SET session_replication_role = replica; "
                + "TRUNCATE perfil.consentimiento_historial, perfil.consentimiento_alcance, perfil.consentimiento, "
                + "perfil.conflicto_perfil, perfil.campo_origen, perfil.cambio_perfil_detalle, perfil.cambio_perfil, "
                + "perfil.direccion, perfil.vinculacion_pendiente, perfil.cliente_origen, perfil.cliente, "
                + "perfil.evento_cliente RESTART IDENTITY; SET session_replication_role = DEFAULT");
    }

    // --- Criterio 1: el otorgamiento queda con fecha, canal, alcance y vigencia ---

    @Test
    void otorgarGuardaFechaCanalAlcanceYVigencia() throws Exception {
        Long id = cliente();

        registrar(id, """
                {"canal": "PRESENCIAL", "alcances": ["GESTION_CLIENTE", "ANALISIS_COMPORTAMIENTO"],
                 "vigenciaDesde": "%s", "vigenciaHasta": "%s", "fechaOtorgamiento": "%sT00:00:00Z"}
                """.formatted(hoy, hoy.plusYears(1), hoy.minusDays(1)))
                .andExpect(status().isOk());

        mvc.perform(get(RUTA, id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("OTORGADO"))
                .andExpect(jsonPath("$.vigente").value(true))
                .andExpect(jsonPath("$.canal").value("PRESENCIAL"))
                .andExpect(jsonPath("$.alcances[0]").value("GESTION_CLIENTE"))
                .andExpect(jsonPath("$.alcances[1]").value("ANALISIS_COMPORTAMIENTO"))
                .andExpect(jsonPath("$.vigenciaDesde").value(hoy.toString()))
                .andExpect(jsonPath("$.vigenciaHasta").value(hoy.plusYears(1).toString()))
                .andExpect(jsonPath("$.fechaOtorgamiento").exists())
                .andExpect(jsonPath("$.actualizadoPor").value("admin"));
        assertThat(gestion.autoriza(id, AlcanceConsentimiento.ANALISIS_COMPORTAMIENTO)).isTrue();
        assertThat(gestion.autoriza(id, AlcanceConsentimiento.COMUNICACIONES_COMERCIALES)).isFalse();
    }

    @Test
    void sinConsentimientoRegistradoResponde404YNoAutorizaNada() throws Exception {
        Long id = cliente();

        mvc.perform(get(RUTA, id)).andExpect(status().isNotFound());
        assertThat(gestion.autoriza(id, AlcanceConsentimiento.GESTION_CLIENTE)).isFalse();
    }

    // --- Criterio 2: la revocación cambia el estado y guarda su fecha ---

    @Test
    void revocarCambiaElEstadoYGuardaLaFecha() throws Exception {
        Long id = cliente();
        registrar(id, otorgamientoBasico()).andExpect(status().isOk());

        mvc.perform(post(RUTA + "/revocacion", id).header("X-Usuario", "admin")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"Lo pidió por correo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REVOCADO"))
                .andExpect(jsonPath("$.vigente").value(false))
                .andExpect(jsonPath("$.fechaRevocacion").exists())
                .andExpect(jsonPath("$.motivoRevocacion").value("Lo pidió por correo"));

        assertThat(jdbc.queryForObject("SELECT fecha_revocacion IS NOT NULL FROM perfil.consentimiento WHERE id_cliente = ?",
                Boolean.class, id)).isTrue();
        assertThat(gestion.autoriza(id, AlcanceConsentimiento.GESTION_CLIENTE)).isFalse();
    }

    @Test
    void noSePuedeRevocarLoQueNoEstaOtorgado() throws Exception {
        Long id = cliente();
        mvc.perform(post(RUTA + "/revocacion", id)).andExpect(status().isConflict());

        registrar(id, otorgamientoBasico());
        mvc.perform(post(RUTA + "/revocacion", id)).andExpect(status().isOk());
        mvc.perform(post(RUTA + "/revocacion", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("no tiene un consentimiento otorgado")));
    }

    // --- Criterio 3: se rechaza una vigencia que termina antes de empezar ---

    @Test
    void rechazaUnaVigenciaConFechaFinalAnteriorALaInicial() throws Exception {
        Long id = cliente();

        registrar(id, """
                {"canal": "CORREO", "alcances": ["GESTION_CLIENTE"],
                 "vigenciaDesde": "%s", "vigenciaHasta": "%s"}
                """.formatted(hoy.plusDays(10), hoy.plusDays(2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La fecha final de la vigencia (" + hoy.plusDays(2)
                        + ") no puede ser anterior a la inicial (" + hoy.plusDays(10) + ")"));

        mvc.perform(get(RUTA, id)).andExpect(status().isNotFound());
        assertThat(filasHistorial(id)).isZero();
    }

    @Test
    void rechazaDatosIncompletos() throws Exception {
        Long id = cliente();

        registrar(id, "{\"alcances\": [\"GESTION_CLIENTE\"]}").andExpect(status().isBadRequest());
        registrar(id, "{\"canal\": \"CORREO\", \"alcances\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Debe autorizar al menos un alcance"));
        registrar(id, "{\"canal\": \"FAX\", \"alcances\": [\"GESTION_CLIENTE\"]}").andExpect(status().isBadRequest());
        registrar(999_999L, otorgamientoBasico()).andExpect(status().isNotFound());
    }

    @Test
    void noSeRegistraConsentimientoEnUnClienteUnificado() throws Exception {
        Long conservado = cliente();
        Cliente absorbido = new Cliente();
        absorbido.consolidarEn(conservado);
        Long idAbsorbido = clientes.save(absorbido).getId();

        registrar(idAbsorbido, otorgamientoBasico())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("ya fue unificado en el cliente " + conservado)));
    }

    // --- Criterio 4: el historial muestra todos los estados con sus fechas ---

    @Test
    void elHistorialMuestraTodosLosEstadosDelMasRecienteAlMasAntiguo() throws Exception {
        Long id = cliente();
        registrar(id, otorgamientoBasico());
        registrar(id, """
                {"canal": "PRESENCIAL", "alcances": ["GESTION_CLIENTE", "SEGMENTACION"]}
                """);
        mvc.perform(post(RUTA + "/revocacion", id).header("X-Usuario", "supervisor"));
        registrar(id, otorgamientoBasico());

        mvc.perform(get(RUTA + "/historial", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].operacion").value("OTORGAMIENTO"))
                .andExpect(jsonPath("$[0].estado").value("OTORGADO"))
                .andExpect(jsonPath("$[0].fechaRevocacion").value(nullValue()))
                .andExpect(jsonPath("$[1].operacion").value("REVOCACION"))
                .andExpect(jsonPath("$[1].estado").value("REVOCADO"))
                .andExpect(jsonPath("$[1].fechaRevocacion").exists())
                .andExpect(jsonPath("$[1].responsable").value("supervisor"))
                .andExpect(jsonPath("$[2].operacion").value("ACTUALIZACION"))
                .andExpect(jsonPath("$[2].alcances.length()").value(2))
                .andExpect(jsonPath("$[3].operacion").value("OTORGAMIENTO"))
                .andExpect(jsonPath("$[3].canal").value("CORREO"))
                .andExpect(jsonPath("$[3].fecha").exists());
    }

    @Test
    void repetirLosMismosDatosNoGeneraHistorial() throws Exception {
        Long id = cliente();
        registrar(id, otorgamientoBasico());
        String actual = mvc.perform(get(RUTA, id)).andReturn().getResponse().getContentAsString();
        String fechaOtorgamiento = com.jayway.jsonpath.JsonPath.read(actual, "$.fechaOtorgamiento");
        String vigenciaDesde = com.jayway.jsonpath.JsonPath.read(actual, "$.vigenciaDesde");

        registrar(id, """
                {"canal": "CORREO", "alcances": ["GESTION_CLIENTE"], "vigenciaDesde": "%s", "fechaOtorgamiento": "%s"}
                """.formatted(vigenciaDesde, fechaOtorgamiento)).andExpect(status().isOk());

        assertThat(filasHistorial(id)).isEqualTo(1);
    }

    // --- DoD: historial de cambios verificado como no editable ---

    @Test
    void elHistorialNoSePuedeModificarNiBorrar() throws Exception {
        Long id = cliente();
        registrar(id, otorgamientoBasico());

        assertThatThrownBy(() -> jdbc.update("UPDATE perfil.consentimiento_historial SET responsable = 'otro'"))
                .hasMessageContaining("solo lectura");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM perfil.consentimiento_historial"))
                .hasMessageContaining("solo lectura");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE perfil.consentimiento_historial"))
                .hasMessageContaining("solo lectura");
        assertThat(filasHistorial(id)).isEqualTo(1);

        mvc.perform(put(RUTA + "/historial", id)).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete(RUTA + "/historial", id)).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete(RUTA, id)).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void laBaseRechazaUnaVigenciaInvertidaAunqueSeSalteElApi() {
        Long id = cliente();

        assertThatThrownBy(() -> jdbc.update("INSERT INTO perfil.consentimiento (id_cliente, estado, canal, "
                + "fecha_otorgamiento, vigencia_desde, vigencia_hasta, actualizado_por) "
                + "VALUES (?, 'OTORGADO', 'CORREO', now(), DATE '2026-10-10', DATE '2026-10-01', 'admin')", id))
                .hasMessageContaining("ck_consentimiento_vigencia");
    }

    // --- Utilidades ---

    private Long cliente() {
        return clientes.save(new Cliente()).getId();
    }

    private ResultActions registrar(Long id, String cuerpo) throws Exception {
        return mvc.perform(put(RUTA, id).header("X-Usuario", "admin")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static String otorgamientoBasico() {
        return "{\"canal\": \"CORREO\", \"alcances\": [\"GESTION_CLIENTE\"]}";
    }

    private int filasHistorial(Long id) {
        return jdbc.queryForObject("SELECT count(*) FROM perfil.consentimiento_historial WHERE id_cliente = ?",
                Integer.class, id);
    }
}
