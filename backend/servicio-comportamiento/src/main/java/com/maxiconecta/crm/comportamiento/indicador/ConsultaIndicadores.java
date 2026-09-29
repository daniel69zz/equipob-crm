package com.maxiconecta.crm.comportamiento.indicador;

import com.maxiconecta.crm.comportamiento.compra.AgregadoComprasCliente;
import com.maxiconecta.crm.comportamiento.compra.Identificador;
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

    public ConsultaIndicadores(AgregadoComprasCliente agregado, Clock reloj) {
        this.agregado = agregado;
        this.reloj = reloj;
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
}
