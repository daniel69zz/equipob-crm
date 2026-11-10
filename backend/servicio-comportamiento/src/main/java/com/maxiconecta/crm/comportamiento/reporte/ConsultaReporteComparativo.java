package com.maxiconecta.crm.comportamiento.reporte;

import com.maxiconecta.crm.comportamiento.comparacion.PeriodosAComparar;
import com.maxiconecta.crm.comportamiento.reporte.ReporteComparativo.Indicador;
import com.maxiconecta.crm.comportamiento.reporte.ReporteComparativo.Periodo;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Arma el reporte comparativo de indicadores de clientes entre dos periodos (SCRUM-496,
 * docs/compra/reporte-comparativo.md). No tiene cálculos propios:
 * <ul>
 *   <li>los dos periodos se validan con {@link PeriodosAComparar} (SCRUM-40): completos, en orden, de hasta
 *       cinco años y sin superponerse (criterio 2);</li>
 *   <li>cada periodo es el reporte de comportamiento de compra de SCRUM-58
 *       ({@link ConsultaReporteComportamiento}), con su misma población y reglas;</li>
 *   <li>la variación es la de SCRUM-40 ({@code comparacion.VariacionConsumo}).</li>
 * </ul>
 */
@Service
public class ConsultaReporteComparativo {

    private final ConsultaReporteComportamiento reporte;
    private final Clock reloj;

    public ConsultaReporteComparativo(ConsultaReporteComportamiento reporte, Clock reloj) {
        this.reporte = reporte;
        this.reloj = reloj;
    }

    public ReporteComparativo generar(PeriodosAComparar periodos, String segmento) {
        ReporteComportamiento a = reporte.generar(periodos.a(), segmento);
        ReporteComportamiento b = reporte.generar(periodos.b(), segmento);
        boolean sinDatos = a.frecuencia().compras() == 0 && b.frecuencia().compras() == 0;
        return new ReporteComparativo(OffsetDateTime.now(reloj), new Periodo(a.desde(), a.hasta()),
                new Periodo(b.desde(), b.hasta()), a.zonaHoraria(), b.alcance(), sinDatos,
                sinDatos ? mensajeSinDatos(b.alcance()) : null, indicadores(a, b));
    }

    /** Por qué no hay nada que comparar: el segmento no tiene clientes, o nadie compró en ningún periodo. */
    private static String mensajeSinDatos(ReporteComportamiento.Alcance alcance) {
        if (alcance.segmento() == null) {
            return "No hay compras vigentes en ninguno de los dos periodos.";
        }
        if (alcance.clientesDelSegmento() == 0) {
            return "El segmento '" + alcance.segmento() + "' no tiene clientes asignados.";
        }
        return "Los clientes del segmento '" + alcance.segmento()
                + "' no tienen compras vigentes en ninguno de los dos periodos.";
    }

    /** Los indicadores de SCRUM-498, en el orden en que se muestran y se exportan. */
    private static List<Indicador> indicadores(ReporteComportamiento a, ReporteComportamiento b) {
        return List.of(
                Indicador.de("COMPRAS", "Compras", entero(a.frecuencia().compras()), entero(b.frecuencia().compras())),
                Indicador.de("CLIENTES_CON_COMPRAS", "Clientes que compraron",
                        entero(a.frecuencia().clientesConCompras()), entero(b.frecuencia().clientesConCompras())),
                Indicador.de("COMPRAS_POR_CLIENTE", "Compras por cliente",
                        a.frecuencia().comprasPorCliente(), b.frecuencia().comprasPorCliente()),
                Indicador.de("VALOR_TOTAL", "Valor total (Bs)", a.valor().total(), b.valor().total()),
                Indicador.de("TICKET_PROMEDIO", "Ticket promedio (Bs)", a.valor().ticketPromedio(), b.valor().ticketPromedio()),
                Indicador.de("VALOR_POR_CLIENTE", "Valor por cliente (Bs)",
                        a.valor().valorPorCliente(), b.valor().valorPorCliente()));
    }

    private static BigDecimal entero(long valor) {
        return BigDecimal.valueOf(valor);
    }
}
