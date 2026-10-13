package com.maxiconecta.crm.comportamiento.indicador;

import com.maxiconecta.crm.comportamiento.compra.LineaConsumoCategoria;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Algoritmo de SCRUM-18 (rama feature/SCRUM-18-categorias-mas-consumidas, cc8b028),
 * adaptado a las filas del agregado compartido actual. Una compra cuenta una vez por categoría;
 * descuenta devoluciones y ordena por monto, compras y nombre. No vuelve a consultar la BD.
 */
public final class RankingCategorias {
    public static final String SIN_CATEGORIA = "Sin categoría";
    private static final Comparator<CategoriaConsumida> ORDEN = Comparator
            .comparing(CategoriaConsumida::monto, Comparator.reverseOrder())
            .thenComparing(CategoriaConsumida::compras, Comparator.reverseOrder())
            .thenComparing(CategoriaConsumida::categoria, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(CategoriaConsumida::categoria);

    private RankingCategorias() { }

    public static List<CategoriaConsumida> calcular(List<LineaConsumoCategoria> compradas,
                                                   List<LineaConsumoCategoria> devueltas) {
        Map<ClaveLinea, Consumo> vigente = new LinkedHashMap<>();
        compradas.forEach(l -> vigente.merge(clave(l), consumo(l), Consumo::mas));
        devueltas.forEach(l -> vigente.merge(clave(l), consumo(l).negado(), Consumo::mas));
        // El indicador distingue una categoría real llamada así del grupo de datos sin asignar.
        Map<Categoria, Acumulado> porCategoria = new LinkedHashMap<>();
        vigente.forEach((clave, consumo) -> {
            if (consumo.unidades() > 0 || consumo.monto().signum() > 0) {
                porCategoria.computeIfAbsent(clave.categoria(), c -> new Acumulado()).sumar(consumo);
            }
        });
        return porCategoria.entrySet().stream().map(e -> new CategoriaConsumida(e.getKey().nombre(),
                e.getValue().compras, e.getValue().unidades, e.getValue().monto, e.getKey().sinAsignar()))
                .sorted(ORDEN).toList();
    }

    private static ClaveLinea clave(LineaConsumoCategoria l) {
        boolean vacia = l.categoria() == null || l.categoria().isBlank();
        return new ClaveLinea(l.idCompra(), new Categoria(vacia ? SIN_CATEGORIA : l.categoria().strip(), vacia));
    }

    private static Consumo consumo(LineaConsumoCategoria l) {
        return new Consumo(l.unidades(), l.monto() == null ? BigDecimal.ZERO : l.monto());
    }

    private record Categoria(String nombre, boolean sinAsignar) { }
    private record ClaveLinea(long idCompra, Categoria categoria) { }
    private record Consumo(long unidades, BigDecimal monto) {
        Consumo mas(Consumo otro) { return new Consumo(unidades + otro.unidades, monto.add(otro.monto)); }
        Consumo negado() { return new Consumo(-unidades, monto.negate()); }
    }
    private static final class Acumulado {
        long compras;
        long unidades;
        BigDecimal monto = BigDecimal.ZERO;
        void sumar(Consumo consumo) {
            compras++;
            unidades += Math.max(consumo.unidades(), 0);
            monto = monto.add(consumo.monto().max(BigDecimal.ZERO));
        }
    }
}
