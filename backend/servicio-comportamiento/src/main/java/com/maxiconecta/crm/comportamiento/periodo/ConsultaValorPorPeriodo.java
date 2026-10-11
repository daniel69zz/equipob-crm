package com.maxiconecta.crm.comportamiento.periodo;

import com.maxiconecta.crm.comportamiento.compra.Identificador;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Valor total y promedio por compra que consumió un cliente en un periodo (SCRUM-347,
 * docs/compra/consumo-por-periodo.md). Se calcula al consultarlo sobre el historial de compras, como
 * el resto de los indicadores: una compra nueva, una devolución o una anulación se reflejan en la
 * consulta siguiente. Cada compra se asigna al día que le corresponde en la zona horaria del negocio.
 */
@Service
public class ConsultaValorPorPeriodo {

    private final AgregadoConsumoPeriodo agregado;
    private final ZoneId zona;

    public ConsultaValorPorPeriodo(AgregadoConsumoPeriodo agregado,
                                   @Value("${comportamiento.consumo-periodo.zona-horaria:America/La_Paz}") ZoneId zona) {
        this.agregado = agregado;
        this.zona = zona;
    }

    public ValorPorPeriodo consultar(List<Identificador> identificadores, LocalDate desde, LocalDate hasta) {
        RangoPeriodo rango = new RangoPeriodo(desde, hasta);
        List<Identificador> distintos = identificadores.stream().distinct().toList();
        return ValorPorPeriodo.de(rango, zona.getId(), agregado.resumir(distintos, rango.inicio(zona), rango.fin(zona)));
    }
}
