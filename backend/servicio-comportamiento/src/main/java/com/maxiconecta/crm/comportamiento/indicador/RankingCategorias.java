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
 * <p>
 * Tolera datos incompletos sin fallar: una categoría nula o en blanco se agrupa como
 * {@link #SIN_CATEGORIA} y un monto nulo cuenta como cero.
 */
public final class RankingCategorias {

    public static final String SIN_CATEGORIA = "Sin categoría";

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
                Acumulado acumulado = porCategoria.computeIfAbsent(clave.categoria(), c -> new Acumulado());
                acumulado.sumar(consumo);
                acumulado.sinCategoria |= clave.sinCategoria();
            }
        });

        return porCategoria.entrySet().stream()
                .map(e -> new CategoriaConsumida(e.getKey(), e.getValue().compras, e.getValue().unidades,
                        e.getValue().monto, e.getValue().sinCategoria))
                .sorted(DE_MAYOR_A_MENOR_CONSUMO)
                .toList();
    }

    private static ClaveLinea clave(LineaCategoria linea) {
        boolean sinCategoria = linea.categoria() == null || linea.categoria().isBlank();
        return new ClaveLinea(linea.idCompra(), sinCategoria ? SIN_CATEGORIA : linea.categoria().strip(), sinCategoria);
    }

    private static Consumo consumo(LineaCategoria linea) {
        return new Consumo(linea.unidades(), linea.monto() == null ? BigDecimal.ZERO : linea.monto());
    }

    private record ClaveLinea(long idCompra, String categoria, boolean sinCategoria) {
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
        private boolean sinCategoria;

        void sumar(Consumo consumo) {
            compras++;
            unidades += Math.max(consumo.unidades(), 0);
            monto = monto.add(consumo.monto().max(BigDecimal.ZERO));
        }
    }
}
