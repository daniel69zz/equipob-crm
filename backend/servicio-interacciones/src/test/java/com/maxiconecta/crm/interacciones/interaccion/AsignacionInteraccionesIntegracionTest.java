package com.maxiconecta.crm.interacciones.interaccion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-457 · Asignación de una interacción a un agente responsable (SCRUM-56,
 * docs/interacciones/asignacion-interacciones.md): el agente queda como responsable (criterio 1), se reasigna, y el
 * responsable debe ser un Agente de Atención activo.
 */
class AsignacionInteraccionesIntegracionTest extends IntegracionInteracciones {

    @BeforeEach
    void agentes() {
        usuarioInterno(AGENTE, "Ana Rojas", "AGENTE_ATENCION", true);
        usuarioInterno(OTRO_AGENTE, "Luis Paz", "AGENTE_ATENCION", true);
        usuarioInterno("agente.inactivo", "Iván Soto", "AGENTE_ATENCION", false);
        usuarioInterno("gerente.gina", "Gina Vera", "GERENTE_COMERCIAL", true);
    }

    @Test
    void elAdministradorAsignaUnAgenteQueQuedaComoResponsable() throws Exception {
        long id = registrarReclamo(AGENTE);
        OffsetDateTime antes = OffsetDateTime.now(ZoneOffset.UTC);

        JsonNode asignada = respuesta(asignar(id, "admin", "ADMINISTRADOR_CRM", cuerpo("responsable", OTRO_AGENTE,
                "version", 1)).andExpect(status().isOk()));

        assertThat(asignada.get("responsable").asText()).isEqualTo(OTRO_AGENTE);
        assertThat(asignada.get("responsableAsignadoPor").asText()).isEqualTo("admin");
        assertThat(OffsetDateTime.parse(asignada.get("responsableAsignadoEn").asText()))
                .isCloseTo(antes, within(1, ChronoUnit.MINUTES));
        assertThat(asignada.get("version").asLong()).isEqualTo(2);
        conIdentidad(get(RUTA_INTERACCION, CLIENTE, id), "gerente", "GERENTE_COMERCIAL", "INTERACCIONES_CONSULTAR")
                .andExpect(jsonPath("$.responsable").value(OTRO_AGENTE));
    }

    @Test
    void unaInteraccionNuevaNoTieneResponsableYSeReasigna() throws Exception {
        long id = registrarReclamo(AGENTE);
        conIdentidad(get(RUTA_INTERACCION, CLIENTE, id), AGENTE, "AGENTE_ATENCION", PERMISOS_AGENTE)
                .andExpect(jsonPath("$.responsable").doesNotExist());

        asignar(id, "admin", "ADMINISTRADOR_CRM", cuerpo("responsable", AGENTE, "version", 1))
                .andExpect(status().isOk());
        asignar(id, "admin", "ADMINISTRADOR_CRM", cuerpo("responsable", OTRO_AGENTE, "version", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responsable").value(OTRO_AGENTE))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void elResponsableDebeSerUnAgenteActivo() throws Exception {
        long id = registrarReclamo(AGENTE);

        for (String invalido : new String[] {"agente.inactivo", "gerente.gina", "no.existe"}) {
            asignar(id, "admin", "ADMINISTRADOR_CRM", cuerpo("responsable", invalido, "version", 1))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.responsable").value("no es un Agente de Atención activo"));
        }
        asignar(id, "admin", "ADMINISTRADOR_CRM", cuerpo("responsable", " ", "version", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.responsable").exists());
        sinResponsable();
    }

    @Test
    void cerradaVersionAnteriorOElMismoResponsableSeRechazan() throws Exception {
        long id = registrarReclamo(AGENTE);
        asignar(id, "admin", "ADMINISTRADOR_CRM", cuerpo("responsable", AGENTE, "version", 5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campos.version").exists());
        asignar(id, "admin", "ADMINISTRADOR_CRM", cuerpo("responsable", AGENTE, "version", 1))
                .andExpect(status().isOk());
        asignar(id, "admin", "ADMINISTRADOR_CRM", cuerpo("responsable", AGENTE, "version", 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La interacción ya tiene ese responsable"));

        seguimiento(id, AGENTE, "AGENTE_ATENCION", cuerpo("estado", "CERRADA", "version", 2));
        asignar(id, "admin", "ADMINISTRADOR_CRM", cuerpo("responsable", OTRO_AGENTE, "version", 3))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La interacción está cerrada: reábrala para modificarla"));
    }

    @Test
    void laListaDeAgentesTraeSoloLosActivos() throws Exception {
        conIdentidad(get("/api/interacciones/agentes"), "admin", "ADMINISTRADOR_CRM", PERMISOS_ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.usuario == 'agente.ana')].nombreCompleto").value("Ana Rojas"))
                .andExpect(jsonPath("$[?(@.usuario == 'agente.inactivo')]").isEmpty())
                .andExpect(jsonPath("$[?(@.usuario == 'gerente.gina')]").isEmpty());
    }

    @Test
    void elHistorialSeFiltraPorResponsable() throws Exception {
        long deAna = registrarReclamo(AGENTE);
        long deLuis = registrarReclamo(AGENTE);
        registrarReclamo(AGENTE);
        asignar(deAna, "admin", "ADMINISTRADOR_CRM", cuerpo("responsable", AGENTE, "version", 1));
        asignar(deLuis, "admin", "ADMINISTRADOR_CRM", cuerpo("responsable", OTRO_AGENTE, "version", 1));

        historial("?responsable=" + AGENTE).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.interacciones[0].id").value(deAna));
        historial("?responsable=" + OTRO_AGENTE + "&estado=ABIERTA").andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.interacciones[0].id").value(deLuis));
        historial("").andExpect(jsonPath("$.total").value(3));
    }

    private void sinResponsable() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM interacciones.interaccion "
                + "WHERE responsable IS NOT NULL OR version > 1", Integer.class)).isZero();
    }
}
