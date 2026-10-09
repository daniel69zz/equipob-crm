package com.maxiconecta.crm.comportamiento.evolucion;

/**
 * Qué datos entraron al cálculo de la evolución y qué hubo que corregir para hacerlo
 * (docs/compra/evolucion-consumo-categoria.md#calidad-de-los-datos-scrum-306). {@code consistente}
 * es verdadero cuando no hubo que corregir nada.
 */
public record CalidadDatos(long comprasAnalizadas, long comprasAnuladasExcluidas, long registrosSinCategoria,
                           long registrosSinMonto, long devolucionesInconsistentes,
                           long comprasConDesgloseInconsistente, boolean consistente) {

    static CalidadDatos de(long comprasAnalizadas, long comprasAnuladasExcluidas, long registrosSinCategoria,
                           long registrosSinMonto, long devolucionesInconsistentes,
                           long comprasConDesgloseInconsistente) {
        boolean consistente = registrosSinCategoria == 0 && registrosSinMonto == 0 && devolucionesInconsistentes == 0
                && comprasConDesgloseInconsistente == 0;
        return new CalidadDatos(comprasAnalizadas, comprasAnuladasExcluidas, registrosSinCategoria, registrosSinMonto,
                devolucionesInconsistentes, comprasConDesgloseInconsistente, consistente);
    }
}
