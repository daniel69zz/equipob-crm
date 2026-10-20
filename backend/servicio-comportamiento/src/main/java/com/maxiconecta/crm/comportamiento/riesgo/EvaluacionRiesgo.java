package com.maxiconecta.crm.comportamiento.riesgo;

import com.maxiconecta.crm.comportamiento.consumo.NivelConsumo;
import com.maxiconecta.crm.comportamiento.frecuencia.ClasificacionFrecuencia;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Resultado de aplicar la fórmula del riesgo de abandono a un cliente (SCRUM-324): el puntaje, el
 * nivel, los datos de entrada ya clasificados y los puntos que aportó cada factor, para poder
 * explicar el nivel sin recalcularlo.
 */
public record EvaluacionRiesgo(BigDecimal puntaje, NivelRiesgo nivel, long diasSinComprar, OffsetDateTime ultimaCompra,
                               long compras, BigDecimal valorAcumulado, ClasificacionFrecuencia clasificacionFrecuencia,
                               NivelConsumo nivelConsumo, boolean inactivo, boolean cambioDetectado,
                               BigDecimal puntosRecencia, BigDecimal puntosFrecuencia, BigDecimal puntosValor,
                               BigDecimal puntosCambio) {
}
