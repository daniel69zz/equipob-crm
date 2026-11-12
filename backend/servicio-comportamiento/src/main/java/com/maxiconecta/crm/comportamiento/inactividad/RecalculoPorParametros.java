package com.maxiconecta.crm.comportamiento.inactividad;

import com.maxiconecta.crm.comportamiento.parametro.CodigosParametros;
import com.maxiconecta.crm.comportamiento.parametro.ParametrosModificados;
import com.maxiconecta.crm.comportamiento.riesgo.CalculadoraRiesgoAbandono;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * SCRUM-515: al cambiar el periodo de inactividad se vuelve a identificar a los clientes inactivos y se recalcula
 * el riesgo de abandono, que mide la recencia con ese periodo. Así la lista y el riesgo usan el valor nuevo desde
 * que se guarda, sin esperar a los procesos de la madrugada.
 */
@Component
public class RecalculoPorParametros {

    private final DetectorClientesInactivos detector;
    private final CalculadoraRiesgoAbandono riesgo;

    public RecalculoPorParametros(DetectorClientesInactivos detector, CalculadoraRiesgoAbandono riesgo) {
        this.detector = detector;
        this.riesgo = riesgo;
    }

    @EventListener
    public void alModificar(ParametrosModificados modificados) {
        if (modificados.incluye(CodigosParametros.INACTIVIDAD_DIAS)) {
            detector.detectar();
            riesgo.recalcularTodos();
        }
    }
}
