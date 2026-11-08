package com.maxiconecta.crm.interacciones.interaccion;

import com.maxiconecta.crm.interacciones.compras.Compra;
import com.maxiconecta.crm.interacciones.compras.ComprasRecientes;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-440 y SCRUM-441 · Consulta del historial del cliente (SCRUM-53, docs/interacciones/historial-cliente.md):
 * las interacciones y las compras del cliente en una línea de tiempo del más reciente al más antiguo, y un cliente
 * sin interacciones sin error.
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

    // --- Compras (SCRUM-441) ---

    @Test
    void lasComprasYLasInteraccionesVanEnUnaSolaLineaDeTiempo() throws Exception {
        long interaccion = registrarReclamo(AGENTE);
        jdbc.update("UPDATE interacciones.interaccion SET registrado_en = '2026-11-06T09:00:00Z' WHERE id = ?",
                interaccion);
        when(compras.recientes(eq(CLIENTE), eq(List.of(ID_VENTAS)), eq(50))).thenReturn(new ComprasRecientes(List.of(
                compra("V-100300", "2026-11-07T10:00:00Z"), compra("V-100234", "2026-11-05T10:15:00Z")), 2, true));

        consultar(CLIENTE, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInteracciones").value(1))
                .andExpect(jsonPath("$.totalCompras").value(2))
                .andExpect(jsonPath("$.comprasDisponibles").value(true))
                .andExpect(jsonPath("$.eventos.length()").value(3))
                .andExpect(jsonPath("$.eventos[0].tipo").value("COMPRA"))
                .andExpect(jsonPath("$.eventos[0].compra.referencia").value("V-100300"))
                .andExpect(jsonPath("$.eventos[0].interaccion").doesNotExist())
                .andExpect(jsonPath("$.eventos[1].tipo").value("INTERACCION"))
                .andExpect(jsonPath("$.eventos[1].interaccion.id").value(interaccion))
                .andExpect(jsonPath("$.eventos[1].compra").doesNotExist())
                .andExpect(jsonPath("$.eventos[2].compra.referencia").value("V-100234"))
                .andExpect(jsonPath("$.eventos[2].compra.montoTotal").value(350.50))
                .andExpect(jsonPath("$.eventos[2].compra.items[0].categoria").value("Electrónica"));
    }

    @Test
    void aIgualFechaLaCompraVaAntesQueLaInteraccion() throws Exception {
        long interaccion = registrarReclamo(AGENTE);
        jdbc.update("UPDATE interacciones.interaccion SET registrado_en = '2026-11-05T10:15:00Z' WHERE id = ?",
                interaccion);
        when(compras.recientes(anyLong(), anyList(), anyInt())).thenReturn(new ComprasRecientes(List.of(
                compra("V-100234", "2026-11-05T10:15:00Z")), 1, true));

        consultar(CLIENTE, "")
                .andExpect(jsonPath("$.eventos[0].tipo").value("COMPRA"))
                .andExpect(jsonPath("$.eventos[1].tipo").value("INTERACCION"));
    }

    @Test
    void unClienteSinInteraccionesVeSusComprasYElAviso() throws Exception {
        when(compras.recientes(anyLong(), anyList(), anyInt())).thenReturn(new ComprasRecientes(List.of(
                compra("V-100234", "2026-11-05T10:15:00Z")), 1, true));

        consultar(CLIENTE, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinInteracciones").value(true))
                .andExpect(jsonPath("$.eventos.length()").value(1))
                .andExpect(jsonPath("$.eventos[0].tipo").value("COMPRA"))
                .andExpect(jsonPath("$.avisos[0]").value("El cliente no tiene interacciones registradas"));
    }

    @Test
    void siComportamientoNoRespondeSeMuestranLasInteraccionesConUnAviso() throws Exception {
        registrarReclamo(AGENTE);
        when(compras.recientes(anyLong(), anyList(), anyInt())).thenReturn(ComprasRecientes.noDisponibles());

        consultar(CLIENTE, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comprasDisponibles").value(false))
                .andExpect(jsonPath("$.eventos.length()").value(1))
                .andExpect(jsonPath("$.avisos[0]")
                        .value("No se pudieron consultar las compras del cliente: se muestran solo sus interacciones"));
    }

    @Test
    void unClienteSinIdentificadoresDeVentasNoConsultaCompras() throws Exception {
        when(perfiles.identificadores(CLIENTE)).thenReturn(Optional.of(List.of()));

        consultar(CLIENTE, "").andExpect(status().isOk()).andExpect(jsonPath("$.totalCompras").value(0));
        verify(compras, never()).recientes(anyLong(), anyList(), anyInt());
    }

    @Test
    void elLimiteCortaLaLineaDeTiempoUnida() throws Exception {
        registrarReclamo(AGENTE);
        when(compras.recientes(anyLong(), anyList(), anyInt())).thenReturn(new ComprasRecientes(List.of(
                compra("V-100300", "2099-01-01T00:00:00Z")), 7, true));

        consultar(CLIENTE, "?limite=1")
                .andExpect(jsonPath("$.eventos.length()").value(1))
                .andExpect(jsonPath("$.eventos[0].tipo").value("COMPRA"))
                .andExpect(jsonPath("$.hayMas").value(true));
        verify(compras).recientes(CLIENTE, List.of(ID_VENTAS), 1);
    }

    static Compra compra(String referencia, String fecha) {
        return new Compra(OffsetDateTime.parse(fecha), referencia, new BigDecimal("350.50"), "CONFIRMADA",
                List.of(new Compra.Item("Electrónica", 1, new BigDecimal("350.50"))));
    }

    ResultActions consultar(long clienteId, String parametros) throws Exception {
        return conIdentidad(get("/api/interacciones/clientes/" + clienteId + "/historial" + parametros), AGENTE,
                "AGENTE_ATENCION", PERMISOS_AGENTE);
    }
}
