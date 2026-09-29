package com.maxiconecta.crm.comportamiento.indicador;

import com.maxiconecta.crm.comportamiento.compra.AgregadoComprasCliente;
import com.maxiconecta.crm.comportamiento.compra.FrecuenciaCompraRepository;
import com.maxiconecta.crm.comportamiento.compra.Identificador;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SCRUM-37 · Consulta del contador persistido para todos los identificadores de un cliente. */
class ConsultaIndicadoresTest {

    private final FrecuenciaCompraRepository frecuencias = mock(FrecuenciaCompraRepository.class);
    private final ConsultaIndicadores consulta = new ConsultaIndicadores(mock(AgregadoComprasCliente.class),
            mock(Clock.class), frecuencias);

    @Test
    void clienteSinIdentificadoresTieneFrecuenciaCero() {
        assertThat(consulta.frecuencia(List.of())).isZero();
        verify(frecuencias, never()).sumar(org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    void sumaLaFrecuenciaPersistidaDeTodosLosIdentificadoresDelCliente() {
        when(frecuencias.sumar(List.of("CLI-5521", "mp-user-3307"))).thenReturn(5L);

        long resultado = consulta.frecuencia(List.of(new Identificador("CLI-5521"),
                new Identificador("mp-user-3307")));

        assertThat(resultado).isEqualTo(5);
        verify(frecuencias).sumar(List.of("CLI-5521", "mp-user-3307"));
    }
}
