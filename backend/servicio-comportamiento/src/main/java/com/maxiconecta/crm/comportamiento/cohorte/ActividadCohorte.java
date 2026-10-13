package com.maxiconecta.crm.comportamiento.cohorte;

import java.time.YearMonth;

/**
 * Cuántos clientes distintos de una cohorte compraron en un mes (docs/compra/retencion-cohortes.md),
 * tal como lo agrega {@link HistorialCohortes}: la entrada del cálculo de retención.
 */
public record ActividadCohorte(YearMonth cohorte, YearMonth mes, long clientes) {
}
