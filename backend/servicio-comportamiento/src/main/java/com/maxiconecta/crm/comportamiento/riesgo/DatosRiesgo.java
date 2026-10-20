package com.maxiconecta.crm.comportamiento.riesgo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Indicadores vigentes de un cliente del CRM con los que se calcula su riesgo de abandono
 * (SCRUM-324): compras y valor acumulado vigentes de todos sus identificadores, la fecha de su última
 * compra vigente y si se le detectó una disminución de su frecuencia o de su monto en el periodo
 * actual (SCRUM-34).
 */
public record DatosRiesgo(long compras, BigDecimal valorAcumulado, OffsetDateTime ultimaCompra,
                          boolean disminucionDetectada) {
}
