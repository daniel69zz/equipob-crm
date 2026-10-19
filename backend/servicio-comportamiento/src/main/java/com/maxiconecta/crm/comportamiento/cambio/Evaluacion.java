package com.maxiconecta.crm.comportamiento.cambio;

import java.util.List;

/**
 * Resultado de evaluar a un cliente contra su línea base: el estado, las compras de la línea base y
 * las variaciones relevantes (vacía salvo en {@link EstadoComportamiento#CON_VARIACION}).
 */
public record Evaluacion(EstadoComportamiento estado, int comprasLineaBase, List<Variacion> variaciones) {
}
