package com.maxiconecta.crm.interacciones.interaccion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-446 · Búsqueda y selección de la compra que se vincula a una interacción (SCRUM-54,
 * docs/interacciones/vinculacion-compras.md): la interacción muestra la compra asociada (criterio 1) y una compra de
 * otro cliente se rechaza (criterio 2).
 */
class VinculacionCompraIntegracionTest extends IntegracionInteracciones {

    @BeforeEach
    void compras() {
        comprasDelCliente();
    }

    @Test
    void elAgenteBuscaLasComprasDelCliente() throws Exception {
        conIdentidad(get("/api/interacciones/clientes/{clienteId}/compras", CLIENTE).param("texto", "V-10"), AGENTE,
                "AGENTE_ATENCION", PERMISOS_AGENTE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.compras[0].referencia").value("V-1001"))
                .andExpect(jsonPath("$.compras[0].montoTotal").value(350.00))
                .andExpect(jsonPath("$.avisos.length()").value(0));
    }

    @Test
    void laBusquedaValidaLosParametrosYElCliente() throws Exception {
        conIdentidad(get("/api/interacciones/clientes/{clienteId}/compras", CLIENTE).param("limite", "0"), AGENTE,
                "AGENTE_ATENCION", PERMISOS_AGENTE)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.limite").exists());
        conIdentidad(get("/api/interacciones/clientes/{clienteId}/compras", 999), AGENTE, "AGENTE_ATENCION",
                PERMISOS_AGENTE)
                .andExpect(status().isNotFound());
    }

    @Test
    void laInteraccionMuestraLaCompraVinculada() throws Exception {
        long id = registrarReclamo(AGENTE);
        OffsetDateTime antes = OffsetDateTime.now(ZoneOffset.UTC);

        JsonNode vinculada = respuesta(vincular(id, AGENTE, "AGENTE_ATENCION", cuerpo("referencia", "V-1001",
                "version", 1)).andExpect(status().isOk()));

        assertThat(vinculada.get("version").asLong()).isEqualTo(2);
        JsonNode compra = vinculada.get("compra");
        assertThat(compra.get("referencia").asText()).isEqualTo("V-1001");
        assertThat(compra.get("montoTotal").decimalValue()).isEqualByComparingTo("350.00");
        assertThat(compra.get("estado").asText()).isEqualTo("CONFIRMADA");
        assertThat(compra.get("vinculadaPor").asText()).isEqualTo(AGENTE);
        assertThat(OffsetDateTime.parse(compra.get("vinculadaEn").asText())).isCloseTo(antes, within(1, ChronoUnit.MINUTES));

        // La consulta de la interacción y el historial la muestran (criterio de aceptación 1).
        conIdentidad(get(RUTA_INTERACCION, CLIENTE, id), AGENTE, "AGENTE_ATENCION", PERMISOS_AGENTE)
                .andExpect(jsonPath("$.compra.referencia").value("V-1001"));
        historial("").andExpect(jsonPath("$.interacciones[0].compra.referencia").value("V-1001"));
    }

    @Test
    void unaCompraDeOtroClienteSeRechaza() throws Exception {
        long id = registrarReclamo(AGENTE);

        vincular(id, AGENTE, "AGENTE_ATENCION", cuerpo("referencia", "V-9999", "version", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.referencia").value("no es una compra del cliente"));

        Map<String, Object> fila = jdbc.queryForMap("SELECT compra_referencia, version FROM interacciones.interaccion");
        assertThat(fila.get("compra_referencia")).isNull();
        assertThat(fila.get("version")).isEqualTo(1L);
    }

    @Test
    void otroAgenteReemplazaYQuitaLaCompraVinculada() throws Exception {
        long id = registrarReclamo(AGENTE);
        vincular(id, AGENTE, "AGENTE_ATENCION", cuerpo("referencia", "V-1001", "version", 1));

        conIdentidad(delete(RUTA_INTERACCION + "/compra", CLIENTE, id).param("version", "2"), OTRO_AGENTE,
                "AGENTE_ATENCION", PERMISOS_AGENTE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compra").doesNotExist())
                .andExpect(jsonPath("$.version").value(3));
    }
}
