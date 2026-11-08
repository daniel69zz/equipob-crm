package com.maxiconecta.crm.interacciones.interaccion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-452 · Clasificación y cambio de estado de una interacción (SCRUM-55,
 * docs/interacciones/seguimiento-interacciones.md): el cambio se aplica, queda registrado (criterio 1) y el
 * historial se filtra por tipo y por estado (criterio 3).
 */
class SeguimientoInteraccionesIntegracionTest extends IntegracionInteracciones {

    @Test
    void elAgenteCambiaElEstadoYElCambioQuedaRegistrado() throws Exception {
        long id = registrarReclamo(AGENTE);
        OffsetDateTime antes = OffsetDateTime.now(ZoneOffset.UTC);

        JsonNode actualizada = respuesta(seguimiento(id, AGENTE, "AGENTE_ATENCION", cuerpo("estado", "EN_PROCESO",
                "comentario", "Se pidió a Ventas el producto faltante", "version", 1)).andExpect(status().isOk()));

        assertThat(actualizada.get("estado").asText()).isEqualTo("EN_PROCESO");
        assertThat(actualizada.get("estadoActualizadoPor").asText()).isEqualTo(AGENTE);
        assertThat(OffsetDateTime.parse(actualizada.get("estadoActualizadoEn").asText()))
                .isCloseTo(antes, within(1, ChronoUnit.MINUTES));
        assertThat(actualizada.get("version").asLong()).isEqualTo(2);
        // El seguimiento no es una corrección del contenido.
        assertThat(actualizada.get("modificadoPor").isNull()).isTrue();

        conIdentidad(get(RUTA_INTERACCION + "/auditoria", CLIENTE, id), "admin", "ADMINISTRADOR_CRM", PERMISOS_ADMIN)
                .andExpect(jsonPath("$[1].operacion").value("SEGUIMIENTO"))
                .andExpect(jsonPath("$[1].usuario").value(AGENTE))
                .andExpect(jsonPath("$[1].version").value(2))
                .andExpect(jsonPath("$[1].cambios.estado.anterior").value("ABIERTA"))
                .andExpect(jsonPath("$[1].cambios.estado.nuevo").value("EN_PROCESO"))
                .andExpect(jsonPath("$[1].comentario").value("Se pidió a Ventas el producto faltante"));

        Map<String, Object> compartido = jdbc.queryForMap("SELECT * FROM auditoria.evento "
                + "WHERE operacion = 'INTERACCION_SEGUIMIENTO_ACTUALIZADO' ORDER BY id DESC LIMIT 1");
        assertThat(compartido.get("usuario")).isEqualTo(AGENTE);
        assertThat(compartido.get("entidad_id")).isEqualTo(String.valueOf(CLIENTE));
        JsonNode detalle = json.readTree((String) compartido.get("detalle"));
        assertThat(detalle.get("estado").asText()).isEqualTo("EN_PROCESO");
        assertThat(detalle.get("campos").toString()).isEqualTo("[\"estado\"]");
    }

    @Test
    void cualquierAgenteDaSeguimientoYReclasificaAunqueNoLaHayaRegistrado() throws Exception {
        long id = registrarReclamo(AGENTE);

        seguimiento(id, OTRO_AGENTE, "AGENTE_ATENCION", cuerpo("tipo", "CONSULTA", "estado", "CERRADA",
                "version", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("CONSULTA"))
                .andExpect(jsonPath("$.estado").value("CERRADA"))
                .andExpect(jsonPath("$.estadoActualizadoPor").value(OTRO_AGENTE));

        Map<String, Object> fila = jdbc.queryForMap("SELECT tipo, estado FROM interacciones.interaccion");
        assertThat(fila).containsEntry("tipo", "CONSULTA").containsEntry("estado", "CERRADA");
    }

    @Test
    void reclasificarSinCambiarElEstadoNoTocaQuienActualizoElEstado() throws Exception {
        long id = registrarReclamo(AGENTE);
        seguimiento(id, AGENTE, "AGENTE_ATENCION", cuerpo("tipo", "NOTA", "version", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("NOTA"))
                .andExpect(jsonPath("$.estado").value("ABIERTA"))
                .andExpect(jsonPath("$.estadoActualizadoPor").doesNotExist());
    }

    @Test
    void unCambioSinNadaNuevoOInvalidoSeRechaza() throws Exception {
        long id = registrarReclamo(AGENTE);
        seguimiento(id, AGENTE, "AGENTE_ATENCION", cuerpo("estado", "ABIERTA", "tipo", "RECLAMO", "version", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La interacción ya tiene ese estado y ese tipo"));
        seguimiento(id, AGENTE, "AGENTE_ATENCION", cuerpo("version", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.estado").exists());
        seguimiento(id, AGENTE, "AGENTE_ATENCION", cuerpo("estado", "RESUELTA", "comentario", "x".repeat(501),
                "version", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.estado").value("debe ser ABIERTA, EN_PROCESO o CERRADA"))
                .andExpect(jsonPath("$.campos.comentario").exists());
        seguimiento(id, AGENTE, "AGENTE_ATENCION", cuerpo("estado", "CERRADA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.version").exists());
        seguimiento(id, AGENTE, "AGENTE_ATENCION", cuerpo("estado", "CERRADA", "version", 5))
                .andExpect(status().isConflict());
        conIdentidad(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(RUTA_INTERACCION, 43, id),
                AGENTE, "AGENTE_ATENCION", PERMISOS_AGENTE, cuerpo("estado", "CERRADA", "version", 1))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT estado FROM interacciones.interaccion", String.class))
                .isEqualTo("ABIERTA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM interacciones.interaccion_auditoria "
                + "WHERE operacion = 'SEGUIMIENTO'", Integer.class)).isZero();
    }

    @Test
    void elGerenteNoCambiaElEstado() throws Exception {
        long id = registrarReclamo(AGENTE);
        conIdentidad(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(RUTA_INTERACCION, CLIENTE, id),
                "gerente", "GERENTE_COMERCIAL", PERMISOS_AGENTE, cuerpo("estado", "CERRADA", "version", 1))
                .andExpect(status().isForbidden());
    }

    @Test
    void elHistorialSeFiltraPorTipoYPorEstado() throws Exception {
        long reclamoAbierto = registrarReclamo(AGENTE);
        long reclamoCerrado = registrarReclamo(AGENTE);
        long consultaEnProceso = respuesta(registrar(AGENTE, "AGENTE_ATENCION", PERMISOS_AGENTE,
                cuerpo("tipo", "CONSULTA", "descripcion", "Pregunta por el envío"))).get("id").asLong();
        seguimiento(reclamoCerrado, AGENTE, "AGENTE_ATENCION", cuerpo("estado", "CERRADA", "version", 1));
        seguimiento(consultaEnProceso, AGENTE, "AGENTE_ATENCION", cuerpo("estado", "EN_PROCESO", "version", 1));

        historial("?estado=ABIERTA").andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.interacciones[0].id").value(reclamoAbierto));
        historial("?estado=en_proceso").andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.interacciones[0].id").value(consultaEnProceso));
        historial("?tipo=RECLAMO&estado=CERRADA").andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.interacciones[0].id").value(reclamoCerrado));
        historial("?tipo=RECLAMO").andExpect(jsonPath("$.total").value(2));
        historial("?tipo=CONSULTA&estado=CERRADA").andExpect(jsonPath("$.total").value(0));
        historial("?estado=RESUELTA").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.estado").exists());
    }
}
