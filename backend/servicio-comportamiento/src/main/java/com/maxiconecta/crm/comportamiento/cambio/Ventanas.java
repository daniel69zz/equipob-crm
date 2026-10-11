package com.maxiconecta.crm.comportamiento.cambio;

import java.time.OffsetDateTime;

/**
 * Límites de una evaluación (docs/compra/cambios-comportamiento.md): la línea base es
 * {@code [inicioBase, inicioActual)} y el periodo actual {@code [inicioActual, fin)}, donde
 * {@code fin} es el momento de la evaluación.
 */
public record Ventanas(OffsetDateTime inicioBase, OffsetDateTime inicioActual, OffsetDateTime fin) {
}
