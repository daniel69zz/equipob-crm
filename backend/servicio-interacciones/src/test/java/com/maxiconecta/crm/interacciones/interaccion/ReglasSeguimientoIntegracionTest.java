package com.maxiconecta.crm.interacciones.interaccion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-453 · Permisos y reglas del seguimiento (SCRUM-55, criterio de aceptación 2,
 * docs/interacciones/seguimiento-interacciones.md): solo el Administrador reabre una interacción cerrada, con un
 * comentario, y una interacción cerrada no se modifica.
 */
class ReglasSeguimientoIntegracionTest extends IntegracionInteracciones {

    private static final String MOTIVO = "El cliente volvió a llamar: el reemplazo tampoco llegó";

    @Test
    void unAgenteNoReabreUnaInteraccionCerrada() throws Exception {
        long id = cerrada();

        seguimiento(id, AGENTE, "AGENTE_ATENCION", cuerpo("estado", "ABIERTA", "comentario", MOTIVO, "version", 2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje")
                        .value("Solo el Administrador de CRM puede reabrir una interacción cerrada"));
        seguimiento(id, AGENTE, "AGENTE_ATENCION", cuerpo("estado", "EN_PROCESO", "comentario", MOTIVO, "version", 2))
                .andExpect(status().isForbidden());

        assertThat(estado()).isEqualTo("CERRADA");
        assertThat(seguimientos()).isEqualTo(1);
    }

    @Test
    void elAdministradorReabreConUnComentarioQueQuedaRegistrado() throws Exception {
        long id = cerrada();

        seguimiento(id, "admin", "ADMINISTRADOR_CRM", cuerpo("estado", "EN_PROCESO", "comentario", MOTIVO,
                "version", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_PROCESO"))
                .andExpect(jsonPath("$.estadoActualizadoPor").value("admin"))
                .andExpect(jsonPath("$.version").value(3));

        conIdentidad(get(RUTA_INTERACCION + "/auditoria", CLIENTE, id), "admin", "ADMINISTRADOR_CRM", PERMISOS_ADMIN)
                .andExpect(jsonPath("$[2].cambios.estado.anterior").value("CERRADA"))
                .andExpect(jsonPath("$[2].cambios.estado.nuevo").value("EN_PROCESO"))
                .andExpect(jsonPath("$[2].comentario").value(MOTIVO));
    }

    @Test
    void reabrirSinComentarioOConUnoMuyCortoSeRechaza() throws Exception {
        long id = cerrada();

        seguimiento(id, "admin", "ADMINISTRADOR_CRM", cuerpo("estado", "ABIERTA", "version", 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.comentario").exists());
        seguimiento(id, "admin", "ADMINISTRADOR_CRM", cuerpo("estado", "ABIERTA", "comentario", "  corto  ",
                "version", 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.comentario").exists());
        assertThat(estado()).isEqualTo("CERRADA");
    }

    @Test
    void unaInteraccionCerradaNoSeReclasificaNiSeCorrige() throws Exception {
        long id = cerrada();

        seguimiento(id, "admin", "ADMINISTRADOR_CRM", cuerpo("tipo", "CONSULTA", "version", 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La interacción está cerrada: reábrala para modificarla"));
        modificar(id, AGENTE, "AGENTE_ATENCION", cuerpo("tipo", "RECLAMO", "descripcion", "Otro texto",
                "version", 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La interacción está cerrada: reábrala para modificarla"));
        modificar(id, "admin", "ADMINISTRADOR_CRM", cuerpo("tipo", "RECLAMO", "descripcion", "Otro texto",
                "version", 2)).andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("SELECT descripcion FROM interacciones.interaccion", String.class))
                .isEqualTo("Llegaron 2 de los 3 productos.");
    }

    @Test
    void alReabrirSePuedeReclasificarEnElMismoCambioYLuegoCorregir() throws Exception {
        long id = cerrada();

        seguimiento(id, "admin", "ADMINISTRADOR_CRM", cuerpo("estado", "ABIERTA", "tipo", "CONSULTA",
                "comentario", MOTIVO, "version", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("CONSULTA"));
        modificar(id, AGENTE, "AGENTE_ATENCION", cuerpo("tipo", "CONSULTA",
                "descripcion", "Consulta por el reemplazo", "version", 3)).andExpect(status().isOk());
    }

    @Test
    void cerrarYPasarEntreAbiertaYEnProcesoNoExigeComentarioNiSerAdministrador() throws Exception {
        long id = registrarReclamo(AGENTE);
        seguimiento(id, OTRO_AGENTE, "AGENTE_ATENCION", cuerpo("estado", "EN_PROCESO", "version", 1))
                .andExpect(status().isOk());
        seguimiento(id, OTRO_AGENTE, "AGENTE_ATENCION", cuerpo("estado", "ABIERTA", "version", 2))
                .andExpect(status().isOk());
        seguimiento(id, AGENTE, "AGENTE_ATENCION", cuerpo("estado", "CERRADA", "version", 3))
                .andExpect(status().isOk());
        // Cerrar de nuevo una cerrada no es un cambio.
        seguimiento(id, AGENTE, "AGENTE_ATENCION", cuerpo("estado", "CERRADA", "version", 4))
                .andExpect(status().isBadRequest());
    }

    private long cerrada() throws Exception {
        long id = registrarReclamo(AGENTE);
        seguimiento(id, AGENTE, "AGENTE_ATENCION", cuerpo("estado", "CERRADA", "comentario",
                "Se envió el producto faltante", "version", 1)).andExpect(status().isOk());
        return id;
    }

    private String estado() {
        return jdbc.queryForObject("SELECT estado FROM interacciones.interaccion", String.class);
    }

    private int seguimientos() {
        return jdbc.queryForObject("SELECT count(*) FROM interacciones.interaccion_auditoria "
                + "WHERE operacion = 'SEGUIMIENTO'", Integer.class);
    }
}
