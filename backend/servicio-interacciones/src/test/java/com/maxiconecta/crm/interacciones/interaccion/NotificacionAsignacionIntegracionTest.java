package com.maxiconecta.crm.interacciones.interaccion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-459 · Notificación al agente cuando se le asigna una interacción (SCRUM-56, criterio de aceptación 1): el
 * agente la ve, solo él, y la marca como leída.
 */
class NotificacionAsignacionIntegracionTest extends IntegracionInteracciones {

    @BeforeEach
    void agentes() {
        usuarioInterno(AGENTE, "Ana Rojas", "AGENTE_ATENCION", true);
        usuarioInterno(OTRO_AGENTE, "Luis Paz", "AGENTE_ATENCION", true);
    }

    @Test
    void elAgenteAsignadoRecibeLaNotificacion() throws Exception {
        long id = registrarReclamo(OTRO_AGENTE);

        asignar(id, "admin", "ADMINISTRADOR_CRM", cuerpo("responsable", AGENTE, "comentario", "Priorizar",
                "version", 1)).andExpect(status().isOk());

        bandeja(AGENTE, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noLeidas").value(1))
                .andExpect(jsonPath("$.notificaciones[0].tipo").value("INTERACCION_ASIGNADA"))
                .andExpect(jsonPath("$.notificaciones[0].interaccionId").value(id))
                .andExpect(jsonPath("$.notificaciones[0].clienteId").value(CLIENTE))
                .andExpect(jsonPath("$.notificaciones[0].mensaje").value("Se le asignó el reclamo n.º " + id
                        + " del cliente " + CLIENTE))
                .andExpect(jsonPath("$.notificaciones[0].comentario").value("Priorizar"))
                .andExpect(jsonPath("$.notificaciones[0].creadaPor").value("admin"))
                .andExpect(jsonPath("$.notificaciones[0].leidaEn").doesNotExist());
        // Solo la ve el agente asignado.
        bandeja(OTRO_AGENTE, "").andExpect(jsonPath("$.noLeidas").value(0))
                .andExpect(jsonPath("$.notificaciones.length()").value(0));
    }

    @Test
    void alReasignarSeNotificaAlNuevoResponsable() throws Exception {
        long id = registrarReclamo(AGENTE);
        asignar(id, "admin", "ADMINISTRADOR_CRM", cuerpo("responsable", AGENTE, "version", 1));
        asignar(id, "admin", "ADMINISTRADOR_CRM", cuerpo("responsable", OTRO_AGENTE, "version", 2));

        bandeja(OTRO_AGENTE, "").andExpect(jsonPath("$.noLeidas").value(1));
        bandeja(AGENTE, "").andExpect(jsonPath("$.noLeidas").value(1));
    }

    @Test
    void elAgenteMarcaLaSuyaComoLeidaYNoLaDeOtro() throws Exception {
        long id = registrarReclamo(AGENTE);
        asignar(id, "admin", "ADMINISTRADOR_CRM", cuerpo("responsable", AGENTE, "version", 1));
        long notificacion = respuesta(bandeja(AGENTE, "")).at("/notificaciones/0/id").asLong();

        conIdentidad(post("/api/interacciones/notificaciones/{id}/leida", notificacion), OTRO_AGENTE,
                "AGENTE_ATENCION", PERMISOS_AGENTE)
                .andExpect(status().isNotFound());
        conIdentidad(post("/api/interacciones/notificaciones/{id}/leida", notificacion), AGENTE,
                "AGENTE_ATENCION", PERMISOS_AGENTE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leidaEn").exists());

        bandeja(AGENTE, "?soloNoLeidas=true").andExpect(jsonPath("$.noLeidas").value(0))
                .andExpect(jsonPath("$.notificaciones.length()").value(0));
        bandeja(AGENTE, "").andExpect(jsonPath("$.notificaciones.length()").value(1));
    }

    @Test
    void unaAsignacionRechazadaNoNotifica() throws Exception {
        long id = registrarReclamo(AGENTE);

        asignar(id, "admin", "ADMINISTRADOR_CRM", cuerpo("responsable", AGENTE, "version", 9))
                .andExpect(status().isConflict());
        asignar(id, AGENTE, "AGENTE_ATENCION", cuerpo("responsable", AGENTE, "version", 1))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM interacciones.notificacion", Integer.class)).isZero();
    }

    @Test
    void sinIdentidadNoHayNotificaciones() throws Exception {
        mvc.perform(get("/api/interacciones/notificaciones")).andExpect(status().isUnauthorized());
    }

    private ResultActions bandeja(String usuario, String consulta) throws Exception {
        return conIdentidad(get("/api/interacciones/notificaciones" + consulta), usuario, "AGENTE_ATENCION",
                PERMISOS_AGENTE);
    }
}
