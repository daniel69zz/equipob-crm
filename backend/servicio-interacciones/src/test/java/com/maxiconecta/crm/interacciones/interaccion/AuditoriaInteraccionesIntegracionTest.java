package com.maxiconecta.crm.interacciones.interaccion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-436 · Auditoría de la creación y de cada modificación de una interacción (SCRUM-52, criterio de
 * aceptación 3): quién, cuándo, la versión y los valores anteriores y nuevos, en la tabla de solo inserción del
 * servicio y en la auditoría compartida.
 */
class AuditoriaInteraccionesIntegracionTest extends IntegracionInteracciones {

    @SpyBean
    AuditoriaInteracciones auditoria;

    private int compartidosAntes;

    @BeforeEach
    void contarCompartidos() {
        // La auditoría compartida es de solo inserción: se cuenta a partir de lo que ya había.
        compartidosAntes = jdbc.queryForObject("SELECT count(*) FROM auditoria.evento "
                + "WHERE operacion LIKE 'INTERACCION_%'", Integer.class);
    }

    @Test
    void laCreacionQuedaConSuResponsableYSusValoresIniciales() throws Exception {
        long id = respuesta(registrar(AGENTE, "AGENTE_ATENCION", PERMISOS_AGENTE, cuerpo("tipo", "RECLAMO",
                "canal", "CHAT", "descripcion", "Llegaron 2 de los 3 productos.", "referenciaCompra", "V-100234"))
                .andExpect(status().isCreated())).get("id").asLong();

        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM interacciones.interaccion_auditoria");
        assertThat(fila.get("interaccion_id")).isEqualTo(id);
        assertThat(fila.get("operacion")).isEqualTo("CREADA");
        assertThat(fila.get("usuario")).isEqualTo(AGENTE);
        assertThat(fila.get("rol")).isEqualTo("AGENTE_ATENCION");
        assertThat(fila.get("version")).isEqualTo(1L);
        JsonNode cambios = json.readTree(jdbc.queryForObject(
                "SELECT cambios::text FROM interacciones.interaccion_auditoria", String.class));
        assertThat(cambios.get("tipo").get("nuevo").asText()).isEqualTo("RECLAMO");
        assertThat(cambios.get("canal").get("nuevo").asText()).isEqualTo("CHAT");
        assertThat(cambios.get("tipo").get("anterior").isNull()).isTrue();
        assertThat(cambios.has("asunto")).isFalse();

        Map<String, Object> compartido = ultimoCompartido();
        assertThat(compartido.get("usuario")).isEqualTo(AGENTE);
        assertThat(compartido.get("operacion")).isEqualTo("INTERACCION_REGISTRADA");
        assertThat(compartido.get("entidad")).isEqualTo("CLIENTE");
        assertThat(compartido.get("entidad_id")).isEqualTo(String.valueOf(CLIENTE));
        JsonNode detalle = json.readTree((String) compartido.get("detalle"));
        assertThat(detalle.get("idInteraccion").asLong()).isEqualTo(id);
        assertThat(detalle.get("tipo").asText()).isEqualTo("RECLAMO");
        // La descripción no sale del servicio: solo el nombre de los campos.
        assertThat(detalle.toString()).doesNotContain("productos");
        assertThat(detalle.get("campos").toString()).contains("descripcion");
    }

    @Test
    void cadaModificacionGuardaQuienCuandoYLosValoresAnterioresYNuevos() throws Exception {
        long id = registrarReclamo(AGENTE);
        modificar(id, AGENTE, "AGENTE_ATENCION", cuerpo("tipo", "RECLAMO", "canal", "TELEFONO",
                "descripcion", "Llegaron 2 de los 3 productos.", "version", 1)).andExpect(status().isOk());
        modificar(id, "admin", "ADMINISTRADOR_CRM", cuerpo("tipo", "CONSULTA", "canal", "TELEFONO",
                "descripcion", "Consulta por el envío pendiente.", "version", 2)).andExpect(status().isOk());

        conIdentidad(get("/api/interacciones/{id}/auditoria", id), "admin", "ADMINISTRADOR_CRM", PERMISOS_ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].operacion").value("CREADA"))
                .andExpect(jsonPath("$[1].operacion").value("MODIFICADA"))
                .andExpect(jsonPath("$[1].usuario").value(AGENTE))
                .andExpect(jsonPath("$[1].version").value(2))
                .andExpect(jsonPath("$[1].cambios.canal.anterior").doesNotExist())
                .andExpect(jsonPath("$[1].cambios.canal.nuevo").value("TELEFONO"))
                .andExpect(jsonPath("$[1].cambios.descripcion").doesNotExist())
                .andExpect(jsonPath("$[2].usuario").value("admin"))
                .andExpect(jsonPath("$[2].rol").value("ADMINISTRADOR_CRM"))
                .andExpect(jsonPath("$[2].version").value(3))
                .andExpect(jsonPath("$[2].cambios.tipo.anterior").value("RECLAMO"))
                .andExpect(jsonPath("$[2].cambios.tipo.nuevo").value("CONSULTA"))
                .andExpect(jsonPath("$[2].cambios.descripcion.anterior").value("Llegaron 2 de los 3 productos."))
                .andExpect(jsonPath("$[2].ocurridoEn").exists());

        Map<String, Object> compartido = ultimoCompartido();
        assertThat(compartido.get("operacion")).isEqualTo("INTERACCION_MODIFICADA");
        assertThat(compartido.get("usuario")).isEqualTo("admin");
        assertThat(json.readTree((String) compartido.get("detalle")).get("campos").toString())
                .isEqualTo("[\"tipo\",\"descripcion\"]");
        assertThat(compartidos()).isEqualTo(3);
    }

    @Test
    void unCambioRechazadoNoDejaAuditoria() throws Exception {
        long id = registrarReclamo(AGENTE);
        modificar(id, OTRO_AGENTE, "AGENTE_ATENCION", cuerpo("tipo", "NOTA", "descripcion", "x", "version", 1))
                .andExpect(status().isForbidden());
        modificar(id, AGENTE, "AGENTE_ATENCION", cuerpo("tipo", "NOTA", "descripcion", "x", "version", 7))
                .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM interacciones.interaccion_auditoria", Integer.class))
                .isEqualTo(1);
        assertThat(compartidos()).isEqualTo(1);
    }

    @Test
    void siLaAuditoriaFallaLaInteraccionNoSeGuardaNiCambia() throws Exception {
        long id = registrarReclamo(AGENTE);
        doThrow(new DataIntegrityViolationException("sin auditoría"))
                .when(auditoria).modificada(any(), anyString(), anyMap());
        doThrow(new DataIntegrityViolationException("sin auditoría"))
                .when(auditoria).creada(any());

        assertThatThrownBy(() -> modificar(id, AGENTE, "AGENTE_ATENCION", cuerpo("tipo", "NOTA",
                "descripcion", "Cambio sin auditar", "version", 1))).hasRootCauseInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> registrarReclamo(AGENTE)).hasRootCauseInstanceOf(DataAccessException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM interacciones.interaccion", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT tipo, version FROM interacciones.interaccion"))
                .containsEntry("tipo", "RECLAMO").containsEntry("version", 1L);
    }

    @Test
    void laAuditoriaEsDeSoloInsercion() throws Exception {
        registrarReclamo(AGENTE);
        assertThatThrownBy(() -> jdbc.update("UPDATE interacciones.interaccion_auditoria SET usuario = 'otro'"))
                .hasMessageContaining("solo lectura");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM interacciones.interaccion_auditoria"))
                .hasMessageContaining("solo lectura");
    }

    @Test
    void soloElAdministradorConAuditoriaConsultarVeLaAuditoria() throws Exception {
        long id = registrarReclamo(AGENTE);
        conIdentidad(get("/api/interacciones/{id}/auditoria", id), AGENTE, "AGENTE_ATENCION", PERMISOS_ADMIN)
                .andExpect(status().isForbidden());
        conIdentidad(get("/api/interacciones/{id}/auditoria", id), "admin", "ADMINISTRADOR_CRM", PERMISOS_AGENTE)
                .andExpect(status().isForbidden());
        conIdentidad(get("/api/interacciones/{id}/auditoria", 9999), "admin", "ADMINISTRADOR_CRM", PERMISOS_ADMIN)
                .andExpect(status().isNotFound());
    }

    private Map<String, Object> ultimoCompartido() {
        return jdbc.queryForMap("SELECT * FROM auditoria.evento WHERE operacion LIKE 'INTERACCION_%' "
                + "ORDER BY id DESC LIMIT 1");
    }

    private int compartidos() {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria.evento WHERE operacion LIKE 'INTERACCION_%'",
                Integer.class) - compartidosAntes;
    }
}
