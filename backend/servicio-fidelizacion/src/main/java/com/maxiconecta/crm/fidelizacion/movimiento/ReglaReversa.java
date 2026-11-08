package com.maxiconecta.crm.fidelizacion.movimiento;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Regla de reversa de puntos por compra anulada o devuelta (SCRUM-538, docs/fidelizacion/reversa-puntos.md).
 * <p>
 * Los puntos se recalculan con la <b>misma regla</b> con la que se acumuló la compra (tasa y monto mínimo
 * de su versión, SCRUM-46) sobre el monto que sigue vigente, y se revierte la diferencia con lo que ya se
 * había revertido:
 * <pre>
 * vigente  = valorOriginal − montoRevertidoAntes − montoDeEstaReversa
 * revertir = puntosOriginales − puntos(vigente) − puntosRevertidosAntes      (nunca menos de 0)
 * </pre>
 * Así una anulación total revierte todos los puntos que quedaban, una devolución parcial solo los del
 * monto devuelto, y la suma de varias devoluciones nunca revierte más puntos de los que generó la compra.
 */
final class ReglaReversa {

    private ReglaReversa() {
    }

    /**
     * @param original              acumulación que se revierte, con la regla con la que se calculó
     * @param montoRevertidoAntes   suma de los montos de las reversas anteriores de la misma compra
     * @param puntosRevertidosAntes suma de los puntos de esas reversas
     * @param monto                 monto que revierte esta reversa
     * @throws EventoInvalidoException si el monto supera lo que sigue vigente de la compra
     */
    static long puntosARevertir(Original original, BigDecimal montoRevertidoAntes, long puntosRevertidosAntes,
                                String idReversa, BigDecimal monto) {
        BigDecimal vigenteAntes = original.valorConsumido().subtract(montoRevertidoAntes);
        if (monto.compareTo(vigenteAntes) > 0) {
            throw new EventoInvalidoException("La reversa " + idReversa + " (" + monto.toPlainString()
                    + ") supera lo que sigue vigente de la transacción " + original.idTransaccion() + " ("
                    + vigenteAntes.toPlainString() + ")");
        }
        long puntosVigentes = puntos(vigenteAntes.subtract(monto), original);
        return Math.max(0, original.puntos() - puntosVigentes - puntosRevertidosAntes);
    }

    /** Puntos de un monto con la regla de la acumulación original: 0 si es cero o no llega al mínimo. */
    static long puntos(BigDecimal monto, Original original) {
        if (monto.signum() <= 0 || monto.compareTo(original.montoMinimo()) < 0) {
            return 0;
        }
        return monto.divide(original.bolivianosPorPunto(), 0, RoundingMode.DOWN).longValueExact();
    }

    /** La acumulación que se revierte, con la regla con la que se calcularon sus puntos. */
    record Original(String idTransaccion, long clienteId, BigDecimal valorConsumido, long puntos,
                    BigDecimal bolivianosPorPunto, BigDecimal montoMinimo, Long versionRegla) {
    }
}
