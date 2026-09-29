package com.maxiconecta.crm.perfil.consulta;

import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteOrigenRepository;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import com.maxiconecta.crm.perfil.comun.RecursoNoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SCRUM-154 · Consolidación de la ficha integral del cliente (SCRUM-10): datos personales,
 * historial de compras, segmento y puntos en una sola respuesta.
 */
class ConsultaFichaIntegralTest {

    private ClienteRepository clientes;
    private ClienteOrigenRepository origenes;
    private HistorialComprasCliente historialCompras;
    private SegmentoDelCliente segmento;
    private PuntosDelCliente puntos;
    private ConsultaFichaIntegral consulta;

    @BeforeEach
    void preparar() {
        clientes = mock(ClienteRepository.class);
        origenes = mock(ClienteOrigenRepository.class);
        historialCompras = mock(HistorialComprasCliente.class);
        segmento = mock(SegmentoDelCliente.class);
        puntos = mock(PuntosDelCliente.class);
        consulta = new ConsultaFichaIntegral(clientes, origenes, historialCompras, segmento, puntos);
    }

    @Test
    void consolidaLosCuatroBloquesDeLaFicha() {
        Cliente cliente = new Cliente();
        cliente.identificar("Ana", "Perez", "CI", "4455667");
        when(clientes.findById(42L)).thenReturn(Optional.of(cliente));
        when(origenes.findByIdClienteOrderByFechaVinculacion(42L)).thenReturn(List.of());
        CompraResponse compra = new CompraResponse(null, "C-1", null, "CONFIRMADA", List.of());
        when(historialCompras.buscar(eq(42L), any())).thenReturn(List.of(compra));
        SegmentoResponse segmentoRespuesta = new SegmentoResponse("FRECUENTE", null, false);
        when(segmento.buscar(42L)).thenReturn(segmentoRespuesta);
        PuntosResponse puntosRespuesta = new PuntosResponse(350, "PLATA", null, false);
        when(puntos.buscar(42L)).thenReturn(puntosRespuesta);

        FichaIntegralResponse ficha = consulta.obtener(42L);

        assertThat(ficha.datosPersonales().nombres()).isEqualTo("Ana");
        assertThat(ficha.historialCompras()).containsExactly(compra);
        assertThat(ficha.segmento()).isEqualTo(segmentoRespuesta);
        assertThat(ficha.puntos()).isEqualTo(puntosRespuesta);
    }

    @Test
    void unClienteSinSegmentoNiPuntosCargaElRestoDeLaFichaNormalmente() {
        Cliente cliente = new Cliente();
        cliente.identificar("Bruno", "Rojas", "CI", "1122334");
        when(clientes.findById(7L)).thenReturn(Optional.of(cliente));
        when(origenes.findByIdClienteOrderByFechaVinculacion(7L)).thenReturn(List.of());
        when(historialCompras.buscar(eq(7L), any())).thenReturn(List.of());
        when(segmento.buscar(7L)).thenReturn(new SegmentoResponse(null, null, true));
        when(puntos.buscar(7L)).thenReturn(new PuntosResponse(null, null, null, true));

        FichaIntegralResponse ficha = consulta.obtener(7L);

        assertThat(ficha.datosPersonales().nombres()).isEqualTo("Bruno");
        assertThat(ficha.segmento().sinDatos()).isTrue();
        assertThat(ficha.puntos().sinDatos()).isTrue();
    }

    @Test
    void unClienteInexistenteLanzaRecursoNoEncontradoSinConsultarLosDemasBloques() {
        when(clientes.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consulta.obtener(99L)).isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(historialCompras, segmento, puntos);
    }
}
