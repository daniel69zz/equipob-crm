package com.maxiconecta.crm.interacciones.interaccion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-456 · Permisos por rol para asignar interacciones (SCRUM-56, criterio de aceptación 3): solo el
 * Administrador de CRM asigna; a cualquier otro usuario el acceso le es denegado y la interacción no cambia.
 */
class PermisosAsignacionIntegracionTest extends IntegracionInteracciones {

    @BeforeEach
    void agentes() {
        usuarioInterno(AGENTE, "Ana Rojas", "AGENTE_ATENCION", true);
    }

    @Test
    void elAgenteNoAsignaNiSiquieraASiMismo() throws Exception {
        long id = registrarReclamo(AGENTE);

        asignar(id, AGENTE, "AGENTE_ATENCION", cuerpo("responsable", AGENTE, "version", 1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Solo el Administrador de CRM puede asignar interacciones"));
        sinResponsable();
    }

    @Test
    void elGerenteYUnAdministradorSinPermisoNoAsignan() throws Exception {
        long id = registrarReclamo(AGENTE);

        conIdentidad(put(RUTA_INTERACCION + "/responsable", CLIENTE, id), "gerente", "GERENTE_COMERCIAL",
                "INTERACCIONES_CONSULTAR", cuerpo("responsable", AGENTE, "version", 1))
                .andExpect(status().isForbidden());
        conIdentidad(put(RUTA_INTERACCION + "/responsable", CLIENTE, id), "admin", "ADMINISTRADOR_CRM",
                "INTERACCIONES_CONSULTAR", cuerpo("responsable", AGENTE, "version", 1))
                .andExpect(status().isForbidden());
        sinResponsable();
    }

    @Test
    void sinIdentidadResponde401YElAccesoSeCompruebaAntesQueElCuerpo() throws Exception {
        long id = registrarReclamo(AGENTE);

        mvc.perform(put(RUTA_INTERACCION + "/responsable", CLIENTE, id).contentType("application/json")
                        .content("{\"responsable\": \"agente.ana\", \"version\": 1}"))
                .andExpect(status().isUnauthorized());
        // Un cuerpo no válido de un agente responde 403, no 400.
        asignar(id, AGENTE, "AGENTE_ATENCION", cuerpo("version", 0)).andExpect(status().isForbidden());
        sinResponsable();
    }

    @Test
    void soloElAdministradorVeLaListaDeAgentes() throws Exception {
        conIdentidad(get("/api/interacciones/agentes"), AGENTE, "AGENTE_ATENCION", PERMISOS_AGENTE)
                .andExpect(status().isForbidden());
        conIdentidad(get("/api/interacciones/agentes"), "admin", "ADMINISTRADOR_CRM", PERMISOS_ADMIN)
                .andExpect(status().isOk());
    }

    @Test
    void elAgenteVeElResponsableYSigueDandoSeguimiento() throws Exception {
        long id = registrarReclamo(AGENTE);
        asignar(id, "admin", "ADMINISTRADOR_CRM", cuerpo("responsable", AGENTE, "version", 1))
                .andExpect(status().isOk());

        historial("").andExpect(jsonPath("$.interacciones[0].responsable").value(AGENTE));
        seguimiento(id, OTRO_AGENTE, "AGENTE_ATENCION", cuerpo("estado", "EN_PROCESO", "version", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responsable").value(AGENTE));
    }

    private void sinResponsable() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM interacciones.interaccion WHERE responsable IS NOT NULL",
                Integer.class)).isZero();
    }
}
