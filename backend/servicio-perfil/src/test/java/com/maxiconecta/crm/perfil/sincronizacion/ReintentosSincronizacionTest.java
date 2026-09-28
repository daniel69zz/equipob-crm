package com.maxiconecta.crm.perfil.sincronizacion;

import com.maxiconecta.crm.perfil.configuracion.PoliticaReintentos;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCRUM-164 · Reintentos ante errores técnicos al sincronizar el perfil (criterio 4).
 */
class ReintentosSincronizacionTest {

    private final BitacoraSincronizacion bitacora = mock(BitacoraSincronizacion.class);
    private final ProcesadorClientes procesador = mock(ProcesadorClientes.class);
    private final SincronizacionClientes sincronizacion = new SincronizacionClientes(bitacora, procesador,
            new PoliticaReintentos(3, Duration.ofMillis(1), 2.0));

    @Test
    void unErrorTecnicoQueSeResuelveTerminaProcesadoYRegistraLosIntentos() {
        when(procesador.aplicar(7L))
                .thenThrow(new CannotCreateTransactionException("la base no responde"))
                .thenThrow(new QueryTimeoutException("tiempo agotado"))
                .thenReturn(new ResultadoSincronizacion(42L, EstadoEventoCliente.PROCESADO, null));

        assertThat(sincronizacion.procesar(7L)).isEqualTo(EstadoEventoCliente.PROCESADO);

        verify(procesador, times(3)).aplicar(7L);
        verify(bitacora).cerrar(7L, EstadoEventoCliente.PROCESADO, 42L, null, 3);
    }

    @Test
    void siElErrorTecnicoPersisteQuedaFallidoTrasAgotarLosIntentos() {
        when(procesador.aplicar(7L)).thenThrow(new CannotCreateTransactionException("la base no responde"));

        assertThat(sincronizacion.procesar(7L)).isEqualTo(EstadoEventoCliente.FALLIDO);

        verify(procesador, times(3)).aplicar(7L);
        verify(bitacora).cerrar(eq(7L), eq(EstadoEventoCliente.FALLIDO), isNull(),
                startsWith("Error técnico tras 3 intento(s): CannotCreateTransactionException"), eq(3));
    }

    @Test
    void unErrorDelMensajeNoSeReintenta() {
        when(procesador.aplicar(7L)).thenThrow(new EventoIlegibleException("Falta el campo obligatorio 'cliente'"));

        assertThat(sincronizacion.procesar(7L)).isEqualTo(EstadoEventoCliente.FALLIDO);

        verify(procesador, times(1)).aplicar(7L);
        verify(bitacora).cerrar(eq(7L), eq(EstadoEventoCliente.FALLIDO), isNull(),
                eq("EventoIlegibleException: Falta el campo obligatorio 'cliente'"), eq(1));
    }

    @Test
    void reconoceLosErroresTransitoriosDeLaBaseAunqueLleguenEnvueltos() {
        assertThat(SincronizacionClientes.esErrorTecnicoTransitorio(new RuntimeException("envoltorio",
                new SQLException("deadlock detected", "40P01")))).isTrue();
        assertThat(SincronizacionClientes.esErrorTecnicoTransitorio(new SQLException("conexión cerrada", "08006")))
                .isTrue();
        assertThat(SincronizacionClientes.esErrorTecnicoTransitorio(new DataIntegrityViolationException("duplicado")))
                .isFalse();
        assertThat(SincronizacionClientes.esErrorTecnicoTransitorio(new IllegalStateException("sin causa"))).isFalse();
    }

    @Test
    void laEsperaCreceEnCadaReintento() {
        PoliticaReintentos politica = new PoliticaReintentos(null, null, null);

        assertThat(politica.maximo()).isEqualTo(3);
        assertThat(politica.esperaTras(1)).isEqualTo(Duration.ofMillis(500));
        assertThat(politica.esperaTras(2)).isEqualTo(Duration.ofSeconds(1));
    }
}
