package com.maxiconecta.crm.comportamiento.ingesta;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.maxiconecta.crm.comportamiento.compra.CompraRepository;
import com.maxiconecta.crm.comportamiento.validacion.ValidadorEventos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Optional;
import java.util.stream.Stream;

import static com.maxiconecta.crm.comportamiento.ingesta.LectorEventosTest.ejemplo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCRUM-244 · Reglas de idempotencia del procesador, sin base de datos.
 */
class ProcesadorComprasIdempotenciaTest {

    private final EventoRecibidoRepository eventos = mock(EventoRecibidoRepository.class);
    private final CompraRepository compras = mock(CompraRepository.class);
    private final EventoProcesadoRepository procesados = mock(EventoProcesadoRepository.class);
    private final LectorEventos lector = new LectorEventos(new ObjectMapper().registerModule(new JavaTimeModule()));
    private final ProcesadorCompras procesador = new ProcesadorCompras(eventos, compras, procesados, lector,
            validadorSinReglas());

    @BeforeEach
    void eventoEnLaBitacora() {
        when(eventos.findById(7L)).thenReturn(Optional.of(
                new EventoRecibido(ejemplo("compra-ventas.json"), null, null, null, null)));
    }

    @Test
    void laClaveEsTipoOrigenYTransaccion() {
        when(procesados.buscar(any())).thenReturn(Optional.empty());

        procesador.procesar(7L);

        verify(procesados).buscar(new ClaveIdempotencia("COMPRA_CONFIRMADA", "VENTAS", "V-100234"));
    }

    @Test
    void unaTransaccionYaProcesadaSeRechazaSinGuardarNada() {
        when(procesados.buscar(any())).thenReturn(Optional.of(new EventoProcesado(
                new ClaveIdempotencia("COMPRA_CONFIRMADA", "VENTAS", "V-100234"), 3L)));

        assertThatThrownBy(() -> procesador.procesar(7L))
                .isInstanceOf(CompraDuplicadaException.class)
                .hasMessage("La compra VENTAS/V-100234 ya fue registrada por el evento 3");
        verify(procesados, never()).saveAndFlush(any());
        verify(compras, never()).save(any());
    }

    @Test
    void siOtraCopiaSeAdelantaLaLlavePrimariaLoConvierteEnDuplicado() {
        when(procesados.buscar(any())).thenReturn(Optional.empty());
        when(procesados.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("no se pudo guardar",
                new SQLException("duplicate key value violates unique constraint \"pk_evento_procesado\"")));

        assertThatThrownBy(() -> procesador.procesar(7L))
                .isInstanceOf(CompraDuplicadaException.class)
                .hasMessageContaining("procesado al mismo tiempo");
        verify(compras, never()).save(any());
    }

    @Test
    void otroErrorDeIntegridadNoSeConfundeConUnDuplicado() {
        when(procesados.buscar(any())).thenReturn(Optional.empty());
        DataIntegrityViolationException otroError = new DataIntegrityViolationException("no se pudo guardar",
                new SQLException("null value in column \"origen\""));
        when(procesados.saveAndFlush(any())).thenThrow(otroError);

        assertThatThrownBy(() -> procesador.procesar(7L)).isSameAs(otroError);
    }

    @Test
    void laCabeceraIncluyeLaTransaccionParaLaTraza() {
        assertThat(lector.cabecera(ejemplo("compra-ventas.json")).idTransaccion()).isEqualTo("V-100234");
        assertThat(lector.cabecera(ejemplo("mensaje-ilegible.txt")).idTransaccion()).isNull();
    }

    @SuppressWarnings("unchecked")
    private static ValidadorEventos validadorSinReglas() {
        ObjectProvider<com.maxiconecta.crm.comportamiento.validacion.ReglaValidacionEvento> proveedor =
                mock(ObjectProvider.class);
        when(proveedor.orderedStream()).thenReturn(Stream.empty());
        return new ValidadorEventos(proveedor);
    }
}
