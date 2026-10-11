package com.maxiconecta.crm.comportamiento.cambio;

import java.math.BigDecimal;

/**
 * Variación relevante de la frecuencia o el monto del periodo actual respecto a lo habitual
 * (docs/compra/cambios-comportamiento.md). {@code relativa} es {@code (actual - habitual) / habitual}:
 * negativa en una disminución, positiva en un aumento.
 */
public record Variacion(TipoVariacion tipo, DireccionVariacion direccion, BigDecimal actual, BigDecimal habitual,
                        BigDecimal relativa) {
}
