package com.maxiconecta.crm.comportamiento.indicador;

import com.maxiconecta.crm.comportamiento.compra.AgregadoCategoriasCliente;
import com.maxiconecta.crm.comportamiento.compra.AgregadoCategoriasCliente.ConsumoPorCategoria;
import com.maxiconecta.crm.comportamiento.compra.AgregadoComprasCliente;
import com.maxiconecta.crm.comportamiento.compra.Identificador;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Indicadores de comportamiento de un cliente, calculados al consultarlos sobre su historial de
 * compras: una compra nueva, una devolución o una anulación se reflejan en la consulta siguiente.
 */
@Service
public class ConsultaIndicadores {

    private final AgregadoComprasCliente agregado;
    private final AgregadoCategoriasCliente agregadoCategorias;

    public ConsultaIndicadores(AgregadoComprasCliente agregado, AgregadoCategoriasCliente agregadoCategorias) {
        this.agregado = agregado;
        this.agregadoCategorias = agregadoCategorias;
    }

    public TicketPromedio ticketPromedio(List<Identificador> identificadores) {
        return TicketPromedio.de(agregado.resumir(identificadores));
    }

    /** Categorías del cliente de mayor a menor consumo; {@code limite} recorta el ranking si no es nulo. */
    public List<CategoriaConsumida> categoriasMasConsumidas(List<Identificador> identificadores, Integer limite) {
        ConsumoPorCategoria consumo = agregadoCategorias.consumir(identificadores);
        List<CategoriaConsumida> ranking = RankingCategorias.calcular(consumo.compradas(), consumo.devueltas());
        return limite == null ? ranking : ranking.stream().limit(limite).toList();
    }
}
