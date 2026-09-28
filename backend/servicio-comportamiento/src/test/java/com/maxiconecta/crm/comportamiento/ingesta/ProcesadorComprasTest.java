package com.maxiconecta.crm.comportamiento.ingesta;

import com.maxiconecta.crm.comportamiento.compra.Compra;
import com.maxiconecta.crm.comportamiento.compra.CompraRepository;
import com.maxiconecta.crm.comportamiento.compra.Origen;
import com.maxiconecta.crm.comportamiento.validacion.EventoInvalidoException;
import com.maxiconecta.crm.comportamiento.validacion.ReglaContratoCompraConfirmada;
import com.maxiconecta.crm.comportamiento.validacion.ReglaValidacionEvento;
import com.maxiconecta.crm.comportamiento.validacion.ValidadorEventos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCRUM-131 · La validación ocurre antes de persistir la compra y sus ítems.
 */
class ProcesadorComprasTest {

    private final EventoRecibidoRepository eventos = mock(EventoRecibidoRepository.class);
    private final CompraRepository compras = mock(CompraRepository.class);
    private final LectorEventos lector = mock(LectorEventos.class);
    private final ProcesadorCompras procesador = new ProcesadorCompras(eventos, compras, lector, validadorReal());

    @Test
    void unEventoValidoContinuaHastaGuardarLaCompra() {
        EventoRecibido recibido = recibido();
        EventoCompraConfirmada evento = evento("5b7a8c1e-3f2d-4e6a-9b1c-2d3e4f5a6b7c");
        when(eventos.findById(7L)).thenReturn(Optional.of(recibido));
        when(lector.leer(recibido.getContenido())).thenReturn(evento);
        when(compras.findByOrigenAndIdCompraOrigen(Origen.VENTAS, "V-100234")).thenReturn(Optional.empty());
        when(compras.save(any(Compra.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Compra guardada = procesador.procesar(7L);

        assertThat(guardada.getIdCompraOrigen()).isEqualTo("V-100234");
        assertThat(guardada.getItems()).hasSize(1);
        assertThat(recibido.getEstado()).isEqualTo(EstadoEvento.PROCESADO);
        verify(compras).save(any(Compra.class));
    }

    @Test
    void unEventoInvalidoNoLlegaAlRepositorioDeCompras() {
        EventoRecibido recibido = recibido();
        when(eventos.findById(7L)).thenReturn(Optional.of(recibido));
        when(lector.leer(recibido.getContenido())).thenReturn(evento("id-no-es-uuid"));

        assertThatThrownBy(() -> procesador.procesar(7L))
                .isInstanceOf(EventoInvalidoException.class)
                .hasMessageContaining("idEvento")
                .hasMessageContaining("UUID");

        verify(compras, never()).findByOrigenAndIdCompraOrigen(any(), any());
        verify(compras, never()).save(any());
        assertThat(recibido.getEstado()).isEqualTo(EstadoEvento.RECIBIDO);
    }

    @SuppressWarnings("unchecked")
    private static ValidadorEventos validadorReal() {
        ObjectProvider<ReglaValidacionEvento> proveedor = mock(ObjectProvider.class);
        when(proveedor.orderedStream()).thenReturn(Stream.of(new ReglaContratoCompraConfirmada()));
        return new ValidadorEventos(proveedor);
    }

    private static EventoRecibido recibido() {
        return new EventoRecibido("contenido original", null, null, null);
    }

    private static EventoCompraConfirmada evento(String idEvento) {
        EventoCompraConfirmada.Item item = new EventoCompraConfirmada.Item(
                "Electrónica", 1, new BigDecimal("350.50"));
        EventoCompraConfirmada.DatosCompra compra = new EventoCompraConfirmada.DatosCompra(
                "V-100234", "CLI-5521", OffsetDateTime.parse("2026-09-27T15:28:10-04:00"),
                new BigDecimal("350.50"), List.of(item));
        return new EventoCompraConfirmada(idEvento, EventoCompraConfirmada.TIPO, "VENTAS",
                OffsetDateTime.parse("2026-09-27T15:30:05-04:00"), compra);
    }
}
