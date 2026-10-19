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

    @MockBean
    private NivelConsumoDelCliente nivelConsumoDelCliente;

    @MockBean
    private CambiosComportamientoDelCliente cambiosComportamientoDelCliente;

    @MockBean
    private ClasificacionFrecuenciaDelCliente clasificacionFrecuenciaDelCliente;

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
        when(nivelConsumoDelCliente.buscar(anyLong(), any()))
                .thenReturn(new NivelConsumoResponse("ALTO", new java.math.BigDecimal("5230.00"), false));
        when(cambiosComportamientoDelCliente.buscar(anyLong(), any())).thenReturn(new CambiosComportamientoResponse(
                "CON_VARIACION", null, List.of(new CambiosComportamientoResponse.Alerta("MONTO", "DISMINUCION",
                new java.math.BigDecimal("100.00"), new java.math.BigDecimal("400.00"),
                new java.math.BigDecimal("-0.75"), null, null, null, "El monto de compra bajó un 75 %")), false));
        when(clasificacionFrecuenciaDelCliente.buscar(anyLong(), any()))
                .thenReturn(new ClasificacionFrecuenciaResponse("FRECUENTE", 12L, false));

        mvc.perform(get("/api/perfil/clientes/" + cliente.getId() + "/ficha-integral"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datosPersonales.nombres").value("Ana"))
                .andExpect(jsonPath("$.historialCompras[0].referencia").value("C-1"))
                .andExpect(jsonPath("$.segmento.segmento").value("FRECUENTE"))
                .andExpect(jsonPath("$.puntos.saldo").value(350))
                .andExpect(jsonPath("$.nivelConsumo.nivel").value("ALTO"))
                .andExpect(jsonPath("$.nivelConsumo.valorAcumulado").value(5230.00))
                .andExpect(jsonPath("$.nivelConsumo.sinDatos").value(false))
                .andExpect(jsonPath("$.cambiosComportamiento.estado").value("CON_VARIACION"))
                .andExpect(jsonPath("$.cambiosComportamiento.alertas[0].tipo").value("MONTO"))
                .andExpect(jsonPath("$.cambiosComportamiento.alertas[0].valorHabitual").value(400.00))
                .andExpect(jsonPath("$.cambiosComportamiento.sinDatos").value(false))
                .andExpect(jsonPath("$.clasificacionFrecuencia.clasificacion").value("FRECUENTE"))
                .andExpect(jsonPath("$.clasificacionFrecuencia.frecuencia").value(12))
                .andExpect(jsonPath("$.clasificacionFrecuencia.sinDatos").value(false));
    }

    @Test
    void unClienteSinSegmentoNiPuntosMuestraEsosBloquesVaciosYElRestoCargaNormal() throws Exception {
        Cliente cliente = new Cliente();
        cliente.identificar("Bruno", "Rojas", "CI", "1122334");
        cliente = clientes.save(cliente);
        when(historialCompras.buscar(any(), any())).thenReturn(List.of());
        when(segmentoDelCliente.buscar(anyLong())).thenReturn(new SegmentoResponse(null, null, true));
        when(puntosDelCliente.buscar(anyLong())).thenReturn(new PuntosResponse(null, null, null, true));
        when(nivelConsumoDelCliente.buscar(anyLong(), any())).thenReturn(new NivelConsumoResponse(null, null, true));
        when(cambiosComportamientoDelCliente.buscar(anyLong(), any())).thenReturn(CambiosComportamientoResponse.SIN_DATOS);
        when(clasificacionFrecuenciaDelCliente.buscar(anyLong(), any())).thenReturn(ClasificacionFrecuenciaResponse.SIN_DATOS);

        mvc.perform(get("/api/perfil/clientes/" + cliente.getId() + "/ficha-integral"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datosPersonales.nombres").value("Bruno"))
                .andExpect(jsonPath("$.segmento.sinDatos").value(true))
                .andExpect(jsonPath("$.puntos.sinDatos").value(true))
                .andExpect(jsonPath("$.nivelConsumo.sinDatos").value(true))
                .andExpect(jsonPath("$.cambiosComportamiento.sinDatos").value(true))
                .andExpect(jsonPath("$.clasificacionFrecuencia.sinDatos").value(true));
    }

    @Test
    void unClienteInexistenteDevuelve404() throws Exception {
        mvc.perform(get("/api/perfil/clientes/999999/ficha-integral"))
                .andExpect(status().isNotFound());
    }
}
