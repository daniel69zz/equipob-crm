package com.maxiconecta.crm.comportamiento.indicador;

import com.maxiconecta.crm.comportamiento.compra.AgregadoComprasCliente;
import com.maxiconecta.crm.comportamiento.compra.Identificador;
import com.maxiconecta.crm.comportamiento.compra.FrecuenciaCompraRepository;
import com.maxiconecta.crm.comportamiento.consumo.CriterioNivelConsumo;
import com.maxiconecta.crm.comportamiento.consumo.NivelConsumoCliente;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

/**
 * Indicadores de comportamiento de un cliente, calculados al consultarlos sobre su historial de
 * compras: una compra nueva, una devolución o una anulación se reflejan en la consulta siguiente.
 */
@Service
public class ConsultaIndicadores {

    private final AgregadoComprasCliente agregado;
    private final Clock reloj;
    private final FrecuenciaCompraRepository frecuencias;
    private final CriterioNivelConsumo criterioNivel;

    public ConsultaIndicadores(AgregadoComprasCliente agregado, Clock reloj,
                               FrecuenciaCompraRepository frecuencias, CriterioNivelConsumo criterioNivel) {
        this.agregado = agregado;
        this.reloj = reloj;
        this.frecuencias = frecuencias;
        this.criterioNivel = criterioNivel;
    }

    public TicketPromedio ticketPromedio(List<Identificador> identificadores) {
        return TicketPromedio.de(agregado.resumir(identificadores));
    }

    public RecenciaCompra recencia(List<Identificador> identificadores) {
        return RecenciaCompra.calcular(agregado.ultimaCompraVigente(identificadores), reloj.instant());
    }

    public ValorAcumulado valorAcumulado(List<Identificador> identificadores) {
        return ValorAcumulado.de(agregado.resumir(identificadores));
    }

    /**
     * Nivel de consumo (SCRUM-27) sobre el valor acumulado de todos los identificadores del
     * cliente, calculado en el momento: nunca queda desactualizado respecto a las compras.
     */
    public NivelConsumoCliente nivelConsumo(List<Identificador> identificadores) {
        return NivelConsumoCliente.de(valorAcumulado(identificadores), criterioNivel);
    }

    public long frecuencia(List<Identificador> identificadores) {
        if (identificadores.isEmpty()) {
            return 0;
        }
        return frecuencias.sumar(identificadores.stream().map(Identificador::idClienteOrigen).toList());
    }
}
