package com.maxiconecta.crm.comportamiento.cohorte;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcula la retención de cada cohorte en los periodos posteriores a su primera compra (SCRUM-330,
 * docs/compra/retencion-cohortes.md). El periodo {@code n} es el mes {@code n} meses después del de la
 * cohorte; el periodo 0 son los clientes de la cohorte (100 %). Solo se calculan los periodos hasta
 * {@code mesActual}: los meses que aún no llegaron no son un 0 %. Cada cohorte del rango aparece aunque
 * no tenga clientes nuevos, para que la matriz no tenga huecos.
 */
public final class CalculadoraRetencion {

    private CalculadoraRetencion() {
    }

    public static List<CohorteRetencion> calcular(List<ActividadCohorte> actividad, YearMonth desde, YearMonth hasta,
                                                  YearMonth mesActual) {
        Map<YearMonth, Map<YearMonth, Long>> porCohorte = new HashMap<>();
        actividad.forEach(fila -> porCohorte.computeIfAbsent(fila.cohorte(), c -> new HashMap<>())
                .merge(fila.mes(), fila.clientes(), Long::sum));

        List<CohorteRetencion> cohortes = new ArrayList<>();
        for (YearMonth cohorte = desde; !cohorte.isAfter(hasta); cohorte = cohorte.plusMonths(1)) {
            Map<YearMonth, Long> meses = porCohorte.getOrDefault(cohorte, Map.of());
            long clientes = meses.getOrDefault(cohorte, 0L);
            List<RetencionPeriodo> periodos = new ArrayList<>();
            if (clientes > 0) {
                long ultimo = ChronoUnit.MONTHS.between(cohorte, mesActual);
                for (int periodo = 0; periodo <= ultimo; periodo++) {
                    YearMonth mes = cohorte.plusMonths(periodo);
                    long retenidos = meses.getOrDefault(mes, 0L);
                    periodos.add(new RetencionPeriodo(periodo, mes, retenidos, porcentaje(retenidos, clientes)));
                }
            }
            cohortes.add(new CohorteRetencion(cohorte, clientes, List.copyOf(periodos)));
        }
        return List.copyOf(cohortes);
    }

    static BigDecimal porcentaje(long retenidos, long clientes) {
        return BigDecimal.valueOf(retenidos).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(clientes), 2, RoundingMode.HALF_UP);
    }

    /** Una fila de la matriz: los clientes de la cohorte y su retención en cada periodo. */
    public record CohorteRetencion(YearMonth cohorte, long clientes, List<RetencionPeriodo> periodos) {
    }

    /** Una celda de la matriz: cuántos clientes de la cohorte compraron en el periodo, y qué porcentaje son. */
    public record RetencionPeriodo(int periodo, YearMonth mes, long clientes, BigDecimal porcentaje) {
    }
}
