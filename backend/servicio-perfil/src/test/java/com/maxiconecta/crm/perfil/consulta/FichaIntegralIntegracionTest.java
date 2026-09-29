package com.maxiconecta.crm.perfil.consulta;

import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-155 · Pruebas funcionales de la consulta de la ficha integral (SCRUM-10): CA1 (todos los
 * bloques en una sola vista) y CA2 (segmento/puntos sin asignar no impiden cargar el resto).
 * El CA3/CA4 (rol no autorizado denegado y auditado) se prueba a nivel del API Gateway
 * (SCRUM-153, SCRUM-156), que es quien exige el permiso y registra la auditoría.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class FichaIntegralIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ClienteRepository clientes;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private HistorialComprasCliente historialCompras;

    @MockBean
    private SegmentoDelCliente segmentoDelCliente;

    @MockBean
    private PuntosDelCliente puntosDelCliente;

    @BeforeEach
    void limpiarBase() {
        jdbc.execute("SET session_replication_role = replica; "
                + "TRUNCATE perfil.consentimiento_historial, perfil.consentimiento_alcance, perfil.consentimiento, perfil.cambio_perfil_detalle, "
                + "perfil.cambio_perfil, perfil.direccion, perfil.vinculacion_pendiente, perfil.cliente_origen, "
                + "perfil.cliente, perfil.evento_cliente "
                + "RESTART IDENTITY; SET session_replication_role = DEFAULT");
    }

    @Test
    void devuelveLosCuatroBloquesDeLaFichaEnUnaSolaConsulta() throws Exception {
        Cliente cliente = new Cliente();
        cliente.identificar("Ana", "Perez", "CI", "4455667");
        cliente.actualizarContacto("ana@example.com", "70011122");
        cliente = clientes.save(cliente);
        when(historialCompras.buscar(any(), any())).thenReturn(List.of(
                new CompraResponse(null, "C-1", null, "CONFIRMADA", List.of())));
        when(segmentoDelCliente.buscar(anyLong())).thenReturn(new SegmentoResponse("FRECUENTE", null, false));
        when(puntosDelCliente.buscar(anyLong())).thenReturn(new PuntosResponse(350, "PLATA", null, false));

        mvc.perform(get("/api/perfil/clientes/" + cliente.getId() + "/ficha-integral"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datosPersonales.nombres").value("Ana"))
                .andExpect(jsonPath("$.historialCompras[0].referencia").value("C-1"))
                .andExpect(jsonPath("$.segmento.segmento").value("FRECUENTE"))
                .andExpect(jsonPath("$.puntos.saldo").value(350));
    }

    @Test
    void unClienteSinSegmentoNiPuntosMuestraEsosBloquesVaciosYElRestoCargaNormal() throws Exception {
        Cliente cliente = new Cliente();
        cliente.identificar("Bruno", "Rojas", "CI", "1122334");
        cliente = clientes.save(cliente);
        when(historialCompras.buscar(any(), any())).thenReturn(List.of());
        when(segmentoDelCliente.buscar(anyLong())).thenReturn(new SegmentoResponse(null, null, true));
        when(puntosDelCliente.buscar(anyLong())).thenReturn(new PuntosResponse(null, null, null, true));

        mvc.perform(get("/api/perfil/clientes/" + cliente.getId() + "/ficha-integral"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datosPersonales.nombres").value("Bruno"))
                .andExpect(jsonPath("$.segmento.sinDatos").value(true))
                .andExpect(jsonPath("$.puntos.sinDatos").value(true));
    }

    @Test
    void unClienteInexistenteDevuelve404() throws Exception {
        mvc.perform(get("/api/perfil/clientes/999999/ficha-integral"))
                .andExpect(status().isNotFound());
    }
}
