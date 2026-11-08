package com.maxiconecta.crm.interacciones.interaccion;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-440 · Consulta del historial del cliente (SCRUM-53, docs/interacciones/historial-cliente.md): las
 * interacciones del cliente del más reciente al más antiguo, y un cliente sin interacciones sin error.
 */
class HistorialClienteIntegracionTest extends IntegracionInteracciones {

    @Test
    void lasInteraccionesVanDeLaMasRecienteALaMasAntiguaConSuEstado() throws Exception {
        long antigua = registrarReclamo(AGENTE);
        long reciente = registrarReclamo(AGENTE);
        jdbc.update("UPDATE interacciones.interaccion SET registrado_en = '2026-10-01T12:00:00Z' WHERE id = ?", antigua);
        seguimiento(reciente, AGENTE, "AGENTE_ATENCION", cuerpo("estado", "EN_PROCESO", "version", 1));

        consultar(CLIENTE, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(CLIENTE))
                .andExpect(jsonPath("$.totalInteracciones").value(2))
                .andExpect(jsonPath("$.sinInteracciones").value(false))
                .andExpect(jsonPath("$.hayMas").value(false))
                .andExpect(jsonPath("$.avisos.length()").value(0))
                .andExpect(jsonPath("$.eventos[0].tipo").value("INTERACCION"))
                .andExpect(jsonPath("$.eventos[0].interaccion.id").value(reciente))
                .andExpect(jsonPath("$.eventos[0].interaccion.estado").value("EN_PROCESO"))
                .andExpect(jsonPath("$.eventos[1].interaccion.id").value(antigua))
                .andExpect(jsonPath("$.eventos[1].fecha").exists());
    }

    @Test
    void unClienteSinInteraccionesSeIndicaSinError() throws Exception {
        consultar(CLIENTE, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinInteracciones").value(true))
                .andExpect(jsonPath("$.totalInteracciones").value(0))
                .andExpect(jsonPath("$.eventos.length()").value(0))
                .andExpect(jsonPath("$.avisos[0]").value("El cliente no tiene interacciones registradas"));
    }

    @Test
    void soloTraeLasDelClienteYRespetaElLimite() throws Exception {
        registrarReclamo(AGENTE);
        registrarReclamo(AGENTE);
        jdbc.update("INSERT INTO interacciones.interaccion (cliente_id, tipo, descripcion, registrado_por) "
                + "VALUES (43, 'NOTA', 'De otro cliente', 'x')");

        consultar(CLIENTE, "?limite=1")
                .andExpect(jsonPath("$.eventos.length()").value(1))
                .andExpect(jsonPath("$.totalInteracciones").value(2))
                .andExpect(jsonPath("$.hayMas").value(true));
        consultar(CLIENTE, "?limite=0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.limite").exists());
        consultar(CLIENTE, "?limite=101").andExpect(status().isBadRequest());
    }

    @Test
    void unClienteQueNoExisteResponde404() throws Exception {
        consultar(999, "").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No existe el cliente 999 en el CRM"));
    }

    ResultActions consultar(long clienteId, String parametros) throws Exception {
        return conIdentidad(get("/api/interacciones/clientes/" + clienteId + "/historial" + parametros), AGENTE,
                "AGENTE_ATENCION", PERMISOS_AGENTE);
    }
}
