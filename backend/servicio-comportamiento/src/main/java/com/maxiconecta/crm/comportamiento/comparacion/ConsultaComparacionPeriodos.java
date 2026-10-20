package com.maxiconecta.crm.comportamiento.comparacion;

import com.maxiconecta.crm.comportamiento.comparacion.ResultadoComparacion.AlcanceComparacion;
import com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException;
import com.maxiconecta.crm.comportamiento.compra.Identificador;
import com.maxiconecta.crm.comportamiento.compra.ResumenCompras;
import com.maxiconecta.crm.comportamiento.periodo.AgregadoConsumoPeriodo;
import com.maxiconecta.crm.comportamiento.periodo.RangoPeriodo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;

/**
 * Compara la frecuencia y el valor de compra de un cliente, o de los clientes de un segmento,
 * entre dos periodos (SCRUM-351, SCRUM-353, docs/compra/comparacion-periodos.md). Reutiliza la
 * agregación del valor por periodo (SCRUM-39), que ya excluye las compras anuladas, y se calcula al
 * consultarla: refleja de inmediato cualquier compra, devolución o anulación nueva.
 */
@Service
public class ConsultaComparacionPeriodos {

    static final int LARGO_MAXIMO_SEGMENTO = 60;

    private final AgregadoConsumoPeriodo agregado;
    private final ClientesDelSegmento clientesDelSegmento;
    private final ZoneId zona;

    public ConsultaComparacionPeriodos(AgregadoConsumoPeriodo agregado, ClientesDelSegmento clientesDelSegmento,
                                       @Value("${comportamiento.consumo-periodo.zona-horaria:America/La_Paz}") ZoneId zona) {
        this.agregado = agregado;
        this.clientesDelSegmento = clientesDelSegmento;
        this.zona = zona;
    }

    /** Un cliente, identificado por todos sus identificadores en Marketplace y Ventas. */
    public ResultadoComparacion compararCliente(List<Identificador> identificadores, PeriodosAComparar periodos) {
        List<Identificador> distintos = identificadores.stream().distinct().toList();
        ResumenCompras a = agregado.resumir(distintos, inicio(periodos.a()), fin(periodos.a()));
        ResumenCompras b = agregado.resumir(distintos, inicio(periodos.b()), fin(periodos.b()));
        return ResultadoComparacion.de(AlcanceComparacion.cliente(), zona.getId(), periodos, a, b, null);
    }

    /** Los clientes asignados a un segmento: solo ellos entran en la agregación (criterio 2). */
    public ResultadoComparacion compararSegmento(String segmento, PeriodosAComparar periodos) {
        String codigo = segmento == null ? "" : segmento.strip();
        if (codigo.isEmpty() || codigo.length() > LARGO_MAXIMO_SEGMENTO) {
            throw new ReglaNegocioException("Indique el segmento a comparar (de 1 a " + LARGO_MAXIMO_SEGMENTO
                    + " caracteres)");
        }
        List<Long> clientes = clientesDelSegmento.idsDe(codigo).stream().distinct().toList();
        AlcanceComparacion alcance = AlcanceComparacion.segmento(codigo, clientes.size());
        if (clientes.isEmpty()) {
            return ResultadoComparacion.de(alcance, zona.getId(), periodos, ResumenCompras.VACIO, ResumenCompras.VACIO,
                    "El segmento '" + codigo + "' no tiene clientes asignados.");
        }
        ResumenCompras a = agregado.resumirPerfiles(clientes, inicio(periodos.a()), fin(periodos.a()));
        ResumenCompras b = agregado.resumirPerfiles(clientes, inicio(periodos.b()), fin(periodos.b()));
        return ResultadoComparacion.de(alcance, zona.getId(), periodos, a, b, null);
    }

    private java.time.OffsetDateTime inicio(RangoPeriodo periodo) {
        return periodo.inicio(zona);
    }

    private java.time.OffsetDateTime fin(RangoPeriodo periodo) {
        return periodo.fin(zona);
    }
}
