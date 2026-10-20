package com.maxiconecta.crm.comportamiento.riesgo;

import com.maxiconecta.crm.comportamiento.metrica.MetricasCliente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Recalcula el riesgo de abandono de un cliente en cuanto se confirma una compra, una anulación o una
 * devolución suya (criterio de aceptación 2 de SCRUM-35). Escucha las mismas métricas que se publican
 * para la segmentación ({@link com.maxiconecta.crm.comportamiento.metrica.PublicadorMetricas}), que solo
 * existen para compras vinculadas a un perfil. Corre después del commit para no revertir la compra si
 * falla: en ese caso el riesgo se corrige en la ejecución programada.
 */
@Component
public class RecalculoRiesgoPorCompra {

    private static final Logger log = LoggerFactory.getLogger(RecalculoRiesgoPorCompra.class);

    private final CalculadoraRiesgoAbandono calculadora;

    public RecalculoRiesgoPorCompra(CalculadoraRiesgoAbandono calculadora) {
        this.calculadora = calculadora;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void recalcular(MetricasCliente metricas) {
        try {
            calculadora.recalcular(metricas.idCliente());
        } catch (RuntimeException ex) {
            log.error("No se pudo recalcular el riesgo de abandono del cliente {} (evento {}): {}",
                    metricas.idCliente(), metricas.idEvento(), ex.getMessage());
        }
    }
}
