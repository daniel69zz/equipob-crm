package com.maxiconecta.crm.comportamiento.ingesta;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCRUM-558 · Resultado en la bitácora según lo que ocurra al procesar.
 */
class IngestaComprasTest {

    private final BitacoraIngesta bitacora = mock(BitacoraIngesta.class);
    private final ProcesadorCompras procesador = mock(ProcesadorCompras.class);
    private final IngestaCompras ingesta = new IngestaCompras(bitacora, procesador);

    @Test
    void primeroAnotaElMensajeYLuegoLoProcesa() {
        when(bitacora.registrarRecepcion("{}")).thenReturn(7L);

        ingesta.recibir("{}");

        verify(procesador).procesar(7L);
        verify(bitacora, never()).marcarFallido(7L, null);
    }

    @Test
    void unaCompraDuplicadaQuedaDescartadaConSuCausa() {
        when(bitacora.registrarRecepcion("{}")).thenReturn(7L);
        when(procesador.procesar(7L)).thenThrow(new CompraDuplicadaException("ya registrada"));

        ingesta.recibir("{}");

        verify(bitacora).marcarDescartado(7L, "ya registrada");
    }

    @Test
    void unErrorAlProcesarDejaElEventoFallidoConSuCausa() {
        when(bitacora.registrarRecepcion("{}")).thenReturn(7L);
        when(procesador.procesar(7L)).thenThrow(new EventoIlegibleException("Falta el campo obligatorio 'origen'"));

        ingesta.recibir("{}");

        verify(bitacora).marcarFallido(7L, "EventoIlegibleException: Falta el campo obligatorio 'origen'");
    }

    @Test
    void laCausaEsElErrorMasEspecifico() {
        DataIntegrityViolationException error = new DataIntegrityViolationException("no se pudo guardar",
                new SQLException("valor nulo en la columna monto"));

        assertThat(IngestaCompras.causa(error)).isEqualTo("SQLException: valor nulo en la columna monto");
    }
}
