package com.maxiconecta.crm.comportamiento.indicador;

import com.maxiconecta.crm.comportamiento.compra.LineaCategoria;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agrupa por categoría lo comprado por un cliente, descontando lo devuelto, y lo ordena de mayor
 * a menor consumo: monto, luego compras y por último nombre (docs/compra/categorias-mas-consumidas.md).
 * No consulta la base: trabaja sobre las filas que le entrega {@code AgregadoCategoriasCliente}.
 */
public final class RankingCategorias {

    private static final Comparator<CategoriaConsumida> DE_MAYOR_A_MENOR_CONSUMO = Comparator
            .comparing(CategoriaConsumida::monto, Comparator.reverseOrder())
            .thenComparing(CategoriaConsumida::compras, Comparator.reverseOrder())
            .thenComparing(CategoriaConsumida::categoria, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(CategoriaConsumida::categoria);

    private RankingCategorias() {
    }

    public static List<CategoriaConsumida> calcular(List<LineaCategoria> compradas, List<LineaCategoria> devueltas) {
        Map<ClaveLinea, Consumo> vigente = new LinkedHashMap<>();
        compradas.forEach(l -> vigente.merge(clave(l), consumo(l), Consumo::mas));
        devueltas.forEach(l -> vigente.merge(clave(l), consumo(l).negado(), Consumo::mas));

        Map<String, Acumulado> porCategoria = new LinkedHashMap<>();
        vigente.forEach((clave, consumo) -> {
            if (consumo.sigueVigente()) {
                porCategoria.computeIfAbsent(clave.categoria(), c -> new Acumulado()).sumar(consumo);
            }
        });

        return porCategoria.entrySet().stream()
                .map(e -> new CategoriaConsumida(e.getKey(), e.getValue().compras, e.getValue().unidades,
                        e.getValue().monto, false))
                .sorted(DE_MAYOR_A_MENOR_CONSUMO)
                .toList();
    }

    private static ClaveLinea clave(LineaCategoria linea) {
        return new ClaveLinea(linea.idCompra(), linea.categoria());
    }

    private static Consumo consumo(LineaCategoria linea) {
        return new Consumo(linea.unidades(), linea.monto());
    }

    private record ClaveLinea(long idCompra, String categoria) {
    }

    private record Consumo(long unidades, BigDecimal monto) {

        Consumo mas(Consumo otro) {
            return new Consumo(unidades + otro.unidades, monto.add(otro.monto));
        }

        Consumo negado() {
            return new Consumo(-unidades, monto.negate());
        }

        /** La categoría sigue en la compra mientras le quede alguna unidad o algún monto. */
        boolean sigueVigente() {
            return unidades > 0 || monto.signum() > 0;
        }
    }

    private static final class Acumulado {
        private long compras;
        private long unidades;
        private BigDecimal monto = BigDecimal.ZERO;

        void sumar(Consumo consumo) {
            compras++;
            unidades += Math.max(consumo.unidades(), 0);
            monto = monto.add(consumo.monto().max(BigDecimal.ZERO));
        }
    }
}
