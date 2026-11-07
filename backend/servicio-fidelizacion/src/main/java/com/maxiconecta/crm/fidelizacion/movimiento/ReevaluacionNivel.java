package com.maxiconecta.crm.fidelizacion.movimiento;

import java.util.Optional;

/**
 * Nivel de fidelización que corresponde a un saldo (criterio de aceptación 4 de SCRUM-48). Se consulta
 * cada vez que un movimiento cambia el saldo, dentro de la misma transacción.
 *
 * <p>Los niveles bronce, plata y oro con sus umbrales son SCRUM-49: esa historia aporta la
 * implementación como un bean de Spring. Mientras no exista, el nivel del cliente no se toca.
 */
public interface ReevaluacionNivel {

    /** @return el nivel que corresponde al saldo, o vacío si no le corresponde ninguno */
    Optional<String> nivelPara(long saldo);
}
