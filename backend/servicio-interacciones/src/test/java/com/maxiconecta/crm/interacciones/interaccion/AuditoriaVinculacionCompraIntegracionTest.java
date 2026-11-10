package com.maxiconecta.crm.interacciones.interaccion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-448 · Auditoría de la asociación entre interacción y compra (SCRUM-54, criterio de aceptación 3): cada
 * vinculación y desvinculación deja quién la hizo y cuándo, en la auditoría de la interacción y en la compartida.
 */
class AuditoriaVinculacionCompraIntegracionTest extends IntegracionInteracciones {

    @BeforeEach
    void compras() {
        comprasDelCliente();
    }

    @Test
    void laVinculacionQuedaEnLaAuditoriaConQuienYCuando() throws Exception {
        long id = registrarReclamo(AGENTE);

        vincular(id, AGENTE, "AGENTE_ATENCION", cuerpo("referencia", "V-1001", "version", 1))
                .andExpect(status().isOk());

        conIdentidad(get(RUTA_INTERACCION + "/auditoria", CLIENTE, id), "admin", "ADMINISTRADOR_CRM", PERMISOS_ADMIN)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].operacion").value("COMPRA_VINCULADA"))
                .andExpect(jsonPath("$[1].usuario").value(AGENTE))
                .andExpect(jsonPath("$[1].rol").value("AGENTE_ATENCION"))
                .andExpect(jsonPath("$[1].ocurridoEn").exists())
                .andExpect(jsonPath("$[1].version").value(2))
                .andExpect(jsonPath("$[1].cambios.compra.anterior").doesNotExist())
                .andExpect(jsonPath("$[1].cambios.compra.nuevo").value("V-1001"));

        Map<String, Object> compartido = jdbc.queryForMap("SELECT * FROM auditoria.evento "
                + "WHERE operacion = 'INTERACCION_COMPRA_VINCULADA' ORDER BY id DESC LIMIT 1");
        assertThat(compartido.get("usuario")).isEqualTo(AGENTE);
        assertThat(compartido.get("entidad")).isEqualTo("CLIENTE");
        assertThat(compartido.get("entidad_id")).isEqualTo(String.valueOf(CLIENTE));
        JsonNode detalle = json.readTree((String) compartido.get("detalle"));
        assertThat(detalle.get("idInteraccion").asLong()).isEqualTo(id);
        assertThat(detalle.get("compra").asText()).isEqualTo("V-1001");
        assertThat(detalle.get("campos").toString()).isEqualTo("[\"compra\"]");
        // La descripción del reclamo no llega a la auditoría compartida.
        assertThat(detalle.toString()).doesNotContain("Llegaron");
    }

    @Test
    void laDesvinculacionTambienQuedaRegistrada() throws Exception {
        long id = registrarReclamo(AGENTE);
        vincular(id, AGENTE, "AGENTE_ATENCION", cuerpo("referencia", "V-1001", "version", 1));

        conIdentidad(delete(RUTA_INTERACCION + "/compra", CLIENTE, id).param("version", "2"), "admin",
                "ADMINISTRADOR_CRM", PERMISOS_ADMIN)
                .andExpect(status().isOk());

        List<Map<String, Object>> registros = jdbc.queryForList("SELECT operacion, usuario, cambios::text AS cambios "
                + "FROM interacciones.interaccion_auditoria WHERE interaccion_id = ? ORDER BY id", id);
        assertThat(registros).extracting(r -> r.get("operacion"))
                .containsExactly("CREADA", "COMPRA_VINCULADA", "COMPRA_DESVINCULADA");
        assertThat(registros.get(2).get("usuario")).isEqualTo("admin");
        assertThat(json.readTree((String) registros.get(2).get("cambios")).at("/compra/anterior").asText())
                .isEqualTo("V-1001");
        assertThat(jdbc.queryForObject("SELECT detalle FROM auditoria.evento "
                + "WHERE operacion = 'INTERACCION_COMPRA_DESVINCULADA' ORDER BY id DESC LIMIT 1", String.class))
                .contains("V-1001");
    }

    @Test
    void unRechazoNoDejaAuditoria() throws Exception {
        long id = registrarReclamo(AGENTE);
        // La auditoría compartida es de solo inserción y guarda lo de las pruebas anteriores.
        long ultimo = jdbc.queryForObject("SELECT coalesce(max(id), 0) FROM auditoria.evento", Long.class);

        vincular(id, AGENTE, "AGENTE_ATENCION", cuerpo("referencia", "V-9999", "version", 1))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM interacciones.interaccion_auditoria "
                + "WHERE operacion LIKE 'COMPRA_%'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria.evento "
                + "WHERE operacion LIKE 'INTERACCION_COMPRA_%' AND id > ?", Integer.class, ultimo)).isZero();
    }
}
