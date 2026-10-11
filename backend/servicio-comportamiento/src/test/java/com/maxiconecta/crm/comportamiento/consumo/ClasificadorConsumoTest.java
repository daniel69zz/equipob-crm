package com.maxiconecta.crm.comportamiento.consumo;

import com.maxiconecta.crm.comportamiento.compra.AgregadoComprasCliente;
import com.maxiconecta.crm.comportamiento.compra.Identificador;
import com.maxiconecta.crm.comportamiento.compra.ResumenCompras;
import com.maxiconecta.crm.comportamiento.compra.ValorCliente;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCRUM-268, SCRUM-272 · Asignación y actualización del nivel de consumo, sin base de datos.
 */
class ClasificadorConsumoTest {

    private final AgregadoComprasCliente agregado = mock(AgregadoComprasCliente.class);
    private final ClienteNivelConsumoRepository repositorio = mock(ClienteNivelConsumoRepository.class);
    private final CriterioNivelConsumo criterio = new CriterioNivelConsumo(new BigDecimal("500"),
            new BigDecimal("5000"));
    private final ClasificadorConsumo clasificador = new ClasificadorConsumo(agregado, repositorio, criterio);

    @Test
    void asignaElNivelAUnClienteNuevoConSuValorAcumulado() {
        cliente("CLI-5521", 3, "750.00");
        when(repositorio.findById("CLI-5521")).thenReturn(Optional.empty());

        Optional<NivelConsumo> nivel = clasificador.reclasificar("CLI-5521");

        assertThat(nivel).contains(NivelConsumo.MEDIO);
        verify(repositorio).save(any(ClienteNivelConsumo.class));
    }

    @Test
    void actualizaElNivelSiElClienteCruzaUnUmbral() {
        cliente("CLI-5521", 6, "5200.00");
        ClienteNivelConsumo existente = new ClienteNivelConsumo("CLI-5521", NivelConsumo.MEDIO,
                new BigDecimal("4900.00"));
        when(repositorio.findById("CLI-5521")).thenReturn(Optional.of(existente));

        clasificador.reclasificar("CLI-5521");

        assertThat(existente.getNivel()).isEqualTo(NivelConsumo.ALTO);
        assertThat(existente.getValorAcumulado()).isEqualByComparingTo("5200.00");
        verify(repositorio, never()).save(any(ClienteNivelConsumo.class));
    }

    @Test
    void unClienteSinComprasVigentesQuedaSinNivelYSeQuitaSuFila() {
        when(agregado.resumir(List.of(new Identificador("CLI-5521")))).thenReturn(ResumenCompras.VACIO);

        Optional<NivelConsumo> nivel = clasificador.reclasificar("CLI-5521");

        assertThat(nivel).isEmpty();
        verify(repositorio).deleteById("CLI-5521");
        verify(repositorio, never()).save(any(ClienteNivelConsumo.class));
    }

    @Test
    void clasificarTodosAplicaElNivelAcadaClienteYQuitaALosQueYaNoTienenCompras() {
        when(agregado.valorVigentePorCliente()).thenReturn(List.of(
                new ValorCliente("CLI-1", new BigDecimal("100.00")),
                new ValorCliente("CLI-2", new BigDecimal("5000.00"))));
        when(repositorio.findById(any())).thenReturn(Optional.empty());
        ClienteNivelConsumo sinCompras = new ClienteNivelConsumo("CLI-3", NivelConsumo.MEDIO, new BigDecimal("900"));
        ClienteNivelConsumo vigente = new ClienteNivelConsumo("CLI-1", NivelConsumo.BAJO, new BigDecimal("100"));
        when(repositorio.findAll()).thenReturn(List.of(vigente, sinCompras));

        int total = clasificador.clasificarTodos();

        assertThat(total).isEqualTo(2);
        verify(repositorio, org.mockito.Mockito.times(2)).save(any(ClienteNivelConsumo.class));
        verify(repositorio).delete(sinCompras);
        verify(repositorio, never()).delete(vigente);
    }

    @Test
    void elNivelCambiaAlCambiarLosUmbralesEnLaSiguienteEjecucion() {
        when(agregado.valorVigentePorCliente()).thenReturn(List.of(new ValorCliente("CLI-1", new BigDecimal("700"))));
        ClienteNivelConsumo existente = new ClienteNivelConsumo("CLI-1", NivelConsumo.MEDIO, new BigDecimal("700"));
        when(repositorio.findById("CLI-1")).thenReturn(Optional.of(existente));
        when(repositorio.findAll()).thenReturn(List.of(existente));
        ClasificadorConsumo conOtrosUmbrales = new ClasificadorConsumo(agregado, repositorio,
                new CriterioNivelConsumo(new BigDecimal("100"), new BigDecimal("600")));

        conOtrosUmbrales.clasificarTodos();

        assertThat(existente.getNivel()).isEqualTo(NivelConsumo.ALTO);
    }

    private void cliente(String idClienteOrigen, long compras, String monto) {
        when(agregado.resumir(List.of(new Identificador(idClienteOrigen))))
                .thenReturn(new ResumenCompras(compras, new BigDecimal(monto)));
    }
}
