package com.maxiconecta.crm.comportamiento.reporte;

import com.maxiconecta.crm.comportamiento.indicador.CategoriaConsumida;
import com.maxiconecta.crm.comportamiento.indicador.TicketPromedio;
import com.maxiconecta.crm.comportamiento.indicador.ValorAcumulado;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Reporte de comportamiento de compra de un grupo de clientes en un periodo (SCRUM-58,
 * docs/compra/reportes-comportamiento.md): frecuencia, valor y categorías, con las mismas reglas que
 * los indicadores de un cliente para que coincidan con ellos (criterio de aceptación 3).
 *
 * <p>{@code sinDatos} es verdadero cuando el grupo no tiene compras vigentes en el periodo, o el
 * segmento no tiene clientes; {@code mensaje} dice cuál de los dos. No es un error.
 */
public record ReporteComportamiento(OffsetDateTime generadoEn, LocalDate desde, LocalDate hasta, String zonaHoraria,
                                    Alcance alcance, boolean sinDatos, String mensaje, Frecuencia frecuencia,
                                    Valor valor, List<Categoria> categorias) {

    private static final int DECIMALES = 2;

    /**
     * El grupo del reporte. {@code segmento} nulo es la cartera completa; {@code clientesDelSegmento}
     * es cuántos clientes tiene asignados el segmento (nulo en la cartera completa).
     */
    public record Alcance(String segmento, Long clientesDelSegmento) {

        static Alcance cartera() {
            return new Alcance(null, null);
        }

        static Alcance segmento(String segmento, long clientes) {
            return new Alcance(segmento, clientes);
        }
    }

    /**
     * Frecuencia de compra (SCRUM-466): las compras vigentes del periodo, cuántos clientes compraron
     * y cuántas compras hizo en promedio cada uno de ellos (nulo si nadie compró).
     */
    public record Frecuencia(long compras, long clientesConCompras, BigDecimal comprasPorCliente) {

        static Frecuencia de(long compras, long clientesConCompras) {
            return new Frecuencia(compras, clientesConCompras, dividir(BigDecimal.valueOf(compras), clientesConCompras));
        }
    }

    /**
     * Valor de compra (SCRUM-467). {@code total} y {@code ticketPromedio} son {@link ValorAcumulado}
     * y {@link TicketPromedio} del grupo, los mismos cálculos que los indicadores del cliente;
     * {@code valorPorCliente} es el total entre los clientes que compraron.
     */
    public record Valor(BigDecimal total, BigDecimal ticketPromedio, BigDecimal valorPorCliente) {

        static Valor de(ValorAcumulado acumulado, TicketPromedio ticket, long clientesConCompras) {
            return new Valor(acumulado.valor(), ticket.valor(), dividir(acumulado.valor(), clientesConCompras));
        }
    }

    /**
     * Una categoría de compra (SCRUM-465), del ranking de SCRUM-18 ({@code indicador.RankingCategorias}).
     * {@code porcentaje} es su monto sobre la suma de los montos de todas las categorías.
     */
    public record Categoria(String categoria, long compras, long unidades, BigDecimal monto, BigDecimal porcentaje,
                            boolean sinCategoria) {

        static List<Categoria> de(List<CategoriaConsumida> ranking) {
            BigDecimal total = ranking.stream().map(CategoriaConsumida::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
            return ranking.stream().map(c -> new Categoria(c.categoria(), c.compras(), c.unidades(), c.monto(),
                    total.signum() == 0 ? BigDecimal.ZERO.setScale(DECIMALES)
                            : c.monto().multiply(BigDecimal.valueOf(100)).divide(total, DECIMALES, RoundingMode.HALF_UP),
                    c.sinCategoria())).toList();
        }
    }

    private static BigDecimal dividir(BigDecimal valor, long entre) {
        return entre <= 0 ? null : valor.divide(BigDecimal.valueOf(entre), DECIMALES, RoundingMode.HALF_UP);
    }
}
