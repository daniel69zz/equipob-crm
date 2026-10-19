package com.maxiconecta.crm.comportamiento.evolucion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Detalle comparativo de la evolución de una categoría (SCRUM-303): cada periodo contra el
 * anterior y contra un periodo base, y la tendencia de la primera a la segunda mitad del rango
 * (docs/compra/evolucion-consumo-categoria.md#comparación-entre-periodos-scrum-303).
 */
public final class ComparacionPeriodos {

    /** Por debajo de esta variación (en %) entre las dos mitades, la categoría se considera estable. */
    static final BigDecimal UMBRAL_ESTABLE = BigDecimal.TEN;

    private ComparacionPeriodos() {
    }

    /**
     * @param indiceBase posición del periodo base dentro de la serie
     * @param periodos   los periodos de la serie, en el mismo orden, para saber cuáles son parciales
     */
    public static SerieComparada comparar(SerieCategoria serie, int indiceBase, List<Periodo> periodos) {
        List<ConsumoPeriodo> consumo = serie.consumo();
        BigDecimal montoBase = consumo.get(indiceBase).monto();
        List<PuntoEvolucion> evolucion = new ArrayList<>();
        for (int i = 0; i < consumo.size(); i++) {
            ConsumoPeriodo actual = consumo.get(i);
            Variacion anterior = i == 0 ? null : Variacion.entre(consumo.get(i - 1).monto(), actual.monto());
            evolucion.add(new PuntoEvolucion(actual.periodo(), actual.monto(), actual.compras(), actual.unidades(),
                    anterior, Variacion.entre(montoBase, actual.monto())));
        }
        return new SerieComparada(serie.categoria(), serie.monto(), serie.compras(), serie.unidades(),
                tendencia(consumo, periodos), List.copyOf(evolucion));
    }

    /**
     * Compara el monto de la primera mitad de los periodos completos con el de la segunda. Con un
     * número impar, el periodo central no entra en ninguna mitad. Los periodos parciales (como el
     * mes en curso) no entran en las mitades: tendrían menos compras solo por estar incompletos.
     * Solo cuentan para no dar por perdido a quien volvió a comprar en el periodo parcial final.
     */
    static Tendencia tendencia(List<ConsumoPeriodo> consumo, List<Periodo> periodos) {
        List<Integer> completos = IntStream.range(0, consumo.size())
                .filter(i -> !periodos.get(i).parcial())
                .boxed()
                .toList();
        if (completos.size() < 2) {
            return Tendencia.SIN_COMPARACION;
        }
        if (consumo.stream().allMatch(c -> c.monto().signum() == 0)) {
            return Tendencia.SIN_CONSUMO;
        }
        int mitad = completos.size() / 2;
        BigDecimal inicial = sumar(consumo, completos.subList(0, mitad));
        BigDecimal reciente = sumar(consumo, completos.subList(completos.size() - mitad, completos.size()));
        if (reciente.signum() == 0) {
            int ultimoCompleto = completos.get(completos.size() - 1);
            boolean volvioAComprar = consumo.subList(ultimoCompleto + 1, consumo.size()).stream()
                    .anyMatch(c -> c.monto().signum() > 0);
            if (!volvioAComprar) {
                return Tendencia.DEJO_DE_COMPRAR;
            }
            return inicial.signum() == 0 ? Tendencia.CRECE : Tendencia.DECRECE;
        }
        if (inicial.signum() == 0) {
            return Tendencia.CRECE;
        }
        BigDecimal variacion = Variacion.entre(inicial, reciente).porcentaje();
        if (variacion.compareTo(UMBRAL_ESTABLE) > 0) {
            return Tendencia.CRECE;
        }
        if (variacion.compareTo(UMBRAL_ESTABLE.negate()) < 0) {
            return Tendencia.DECRECE;
        }
        return Tendencia.ESTABLE;
    }

    private static BigDecimal sumar(List<ConsumoPeriodo> consumo, List<Integer> indices) {
        return indices.stream().map(i -> consumo.get(i).monto()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
