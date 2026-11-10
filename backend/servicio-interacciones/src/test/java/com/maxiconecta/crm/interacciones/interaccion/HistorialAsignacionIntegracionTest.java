package com.maxiconecta.crm.interacciones.interaccion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-458 · Registro del cambio de responsable en el historial de la interacción (SCRUM-56, criterio de
 * aceptación 2): quién lo hizo, el responsable anterior, el nuevo y la fecha.
 */
class HistorialAsignacionIntegracionTest extends IntegracionInteracciones {

    @BeforeEach
    void agentes() {
        usuarioInterno(AGENTE, "Ana Rojas", "AGENTE_ATENCION", true);
        usuarioInterno(OTRO_AGENTE, "Luis Paz", "AGENTE_ATENCION", true);
    }

    @Test
    void laReasignacionQuedaConElResponsableAnteriorYElNuevo() throws Exception {
        long id = registrarReclamo(AGENTE);
        asignar(id, "admin", "ADMINISTRADOR_CRM", cuerpo("responsable", AGENTE, "version", 1));
        asignar(id, "admin", "ADMINISTRADOR_CRM", cuerpo("responsable", OTRO_AGENTE,
                "comentario", "Ana está de vacaciones", "version", 2)).andExpect(status().isOk());

        conIdentidad(get(RUTA_INTERACCION + "/auditoria", CLIENTE, id), "admin", "ADMINISTRADOR_CRM", PERMISOS_ADMIN)
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[1].operacion").value("ASIGNADA"))
                .andExpect(jsonPath("$[1].cambios.responsable.anterior").doesNotExist())
                .andExpect(jsonPath("$[1].cambios.responsable.nuevo").value(AGENTE))
                .andExpect(jsonPath("$[2].operacion").value("ASIGNADA"))
                .andExpect(jsonPath("$[2].usuario").value("admin"))
                .andExpect(jsonPath("$[2].rol").value("ADMINISTRADOR_CRM"))
                .andExpect(jsonPath("$[2].ocurridoEn").exists())
                .andExpect(jsonPath("$[2].version").value(3))
                .andExpect(jsonPath("$[2].cambios.responsable.anterior").value(AGENTE))
                .andExpect(jsonPath("$[2].cambios.responsable.nuevo").value(OTRO_AGENTE))
                .andExpect(jsonPath("$[2].comentario").value("Ana está de vacaciones"));

        Map<String, Object> compartido = jdbc.queryForMap("SELECT * FROM auditoria.evento "
                + "WHERE operacion = 'INTERACCION_ASIGNADA' ORDER BY id DESC LIMIT 1");
        assertThat(compartido.get("usuario")).isEqualTo("admin");
        assertThat(compartido.get("entidad_id")).isEqualTo(String.valueOf(CLIENTE));
        JsonNode detalle = json.readTree((String) compartido.get("detalle"));
        assertThat(detalle.get("responsable").asText()).isEqualTo(OTRO_AGENTE);
        assertThat(detalle.get("responsableAnterior").asText()).isEqualTo(AGENTE);
        assertThat(detalle.get("campos").toString()).isEqualTo("[\"responsable\"]");
    }

    @Test
    void unaAsignacionRechazadaNoDejaHistorial() throws Exception {
        long id = registrarReclamo(AGENTE);
        long ultimo = jdbc.queryForObject("SELECT coalesce(max(id), 0) FROM auditoria.evento", Long.class);

        asignar(id, AGENTE, "AGENTE_ATENCION", cuerpo("responsable", AGENTE, "version", 1))
                .andExpect(status().isForbidden());
        asignar(id, "admin", "ADMINISTRADOR_CRM", cuerpo("responsable", "no.existe", "version", 1))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM interacciones.interaccion_auditoria "
                + "WHERE operacion = 'ASIGNADA'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria.evento WHERE operacion = 'INTERACCION_ASIGNADA' "
                + "AND id > ?", Integer.class, ultimo)).isZero();
    }
}
