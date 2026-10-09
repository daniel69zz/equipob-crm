package com.maxiconecta.crm.comportamiento.evolucion;

import com.maxiconecta.crm.comportamiento.compra.AgregadoConsumoCategorias.CompraEnRango;
import com.maxiconecta.crm.comportamiento.compra.AgregadoConsumoCategorias.ConsumoEnRango;
import com.maxiconecta.crm.comportamiento.compra.LineaConsumoCategoria;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Agrupa por categoría y periodo el consumo vigente de un cliente: descuenta de cada compra lo
 * devuelto en cada categoría y suma lo que queda en el periodo de la compra, con todos los
 * periodos del rango aunque no tengan compras (docs/compra/evolucion-consumo-categoria.md).
 * No consulta la base: trabaja sobre las filas que entrega {@code AgregadoConsumoCategorias}, con
 * su categoría y su monto presentes, como los exige la base.
 */
public final class CalculoEvolucionConsumo {

    /** De mayor a menor consumo en el rango: monto, compras y por último nombre. */
    private static final Comparator<SerieCategoria> DE_MAYOR_A_MENOR_CONSUMO = Comparator
            .comparing(SerieCategoria::monto, Comparator.reverseOrder())
            .thenComparing(SerieCategoria::compras, Comparator.reverseOrder())
            .thenComparing(SerieCategoria::categoria, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(SerieCategoria::categoria);

    private static final Comparator<String> ALFABETICO =
            String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());

    private CalculoEvolucionConsumo() {
    }

    /**
     * @param categoriasFiltro vacío para todas las categorías con consumo; si no, solo esas, en
     *                         cero en los periodos (o en todo el rango) en que no tengan consumo
     */
    public static Resultado calcular(List<Periodo> periodos, ZoneId zona, ConsumoEnRango consumo,
                                     Set<String> categoriasFiltro) {
        Map<Long, String> periodoDeCompra = periodoDeCadaCompra(periodos, zona, consumo.compras());

        Map<ClaveLinea, Consumo> vigente = new LinkedHashMap<>();
        consumo.compradas().forEach(l -> vigente.merge(clave(l), consumo(l), Consumo::mas));
        consumo.devueltas().forEach(l -> vigente.merge(clave(l), consumo(l).negado(), Consumo::mas));

        Set<String> disponibles = new TreeSet<>(ALFABETICO);
        Map<String, Map<String, Acumulado>> porCategoria = new HashMap<>();
        Map<String, Acumulado> porPeriodo = new HashMap<>();
        vigente.forEach((clave, consumoLinea) -> {
            String periodo = periodoDeCompra.get(clave.idCompra());
            if (periodo == null || !consumoLinea.sigueVigente()) {
                return;
            }
            disponibles.add(clave.categoria());
            if (!categoriasFiltro.isEmpty() && !categoriasFiltro.contains(clave.categoria())) {
                return;
            }
            porCategoria.computeIfAbsent(clave.categoria(), c -> new HashMap<>())
                    .computeIfAbsent(periodo, p -> new Acumulado())
                    .sumar(clave.idCompra(), consumoLinea);
            porPeriodo.computeIfAbsent(periodo, p -> new Acumulado()).sumar(clave.idCompra(), consumoLinea);
        });

        Collection<String> categorias = categoriasFiltro.isEmpty() ? porCategoria.keySet() : categoriasFiltro;
        List<SerieCategoria> series = categorias.stream()
                .map(categoria -> serie(categoria, periodos, porCategoria.getOrDefault(categoria, Map.of())))
                .sorted(DE_MAYOR_A_MENOR_CONSUMO)
                .toList();
        List<TotalPeriodo> totales = periodos.stream()
                .map(p -> porPeriodo.getOrDefault(p.clave(), new Acumulado()).total(p.clave()))
                .toList();
        return new Resultado(series, totales, List.copyOf(disponibles));
    }

    /** Periodo de cada compra según su fecha en la zona horaria del negocio. */
    private static Map<Long, String> periodoDeCadaCompra(List<Periodo> periodos, ZoneId zona,
                                                         List<CompraEnRango> compras) {
        Map<Long, String> periodoDeCompra = new HashMap<>();
        for (CompraEnRango compra : compras) {
            LocalDate dia = compra.fecha().atZoneSameInstant(zona).toLocalDate();
            periodos.stream().filter(p -> p.contiene(dia)).findFirst()
                    .ifPresent(p -> periodoDeCompra.put(compra.idCompra(), p.clave()));
        }
        return periodoDeCompra;
    }

    private static SerieCategoria serie(String categoria, List<Periodo> periodos, Map<String, Acumulado> porPeriodo) {
        List<ConsumoPeriodo> consumo = new ArrayList<>();
        BigDecimal monto = BigDecimal.ZERO;
        long compras = 0;
        long unidades = 0;
        for (Periodo periodo : periodos) {
            Acumulado acumulado = porPeriodo.get(periodo.clave());
            ConsumoPeriodo enPeriodo = acumulado == null
                    ? ConsumoPeriodo.sinConsumo(periodo.clave())
                    : acumulado.consumo(periodo.clave());
            consumo.add(enPeriodo);
            monto = monto.add(enPeriodo.monto());
            compras += enPeriodo.compras();
            unidades += enPeriodo.unidades();
        }
        return new SerieCategoria(categoria, monto, compras, unidades, List.copyOf(consumo));
    }

    private static ClaveLinea clave(LineaConsumoCategoria linea) {
        return new ClaveLinea(linea.idCompra(), linea.categoria());
    }

    private static Consumo consumo(LineaConsumoCategoria linea) {
        return new Consumo(linea.unidades(), linea.monto());
    }

    /** Series ordenadas de mayor a menor consumo, totales por periodo y todas las categorías con consumo. */
    public record Resultado(List<SerieCategoria> series, List<TotalPeriodo> totales,
                            List<String> categoriasDisponibles) {
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

    /** Lo acumulado en un periodo: cada compra se cuenta una vez aunque aporte varias categorías. */
    private static final class Acumulado {
        private final Set<Long> compras = new HashSet<>();
        private long unidades;
        private BigDecimal monto = BigDecimal.ZERO;

        void sumar(long idCompra, Consumo consumo) {
            compras.add(idCompra);
            unidades += Math.max(consumo.unidades(), 0);
            monto = monto.add(consumo.monto().max(BigDecimal.ZERO));
        }

        ConsumoPeriodo consumo(String periodo) {
            return new ConsumoPeriodo(periodo, monto, compras.size(), unidades);
        }

        TotalPeriodo total(String periodo) {
            return new TotalPeriodo(periodo, monto, compras.size());
        }
    }
}
