package com.maxiconecta.crm.fidelizacion.consulta;

import com.maxiconecta.crm.fidelizacion.caducidad.SaldoVigente;
import com.maxiconecta.crm.fidelizacion.movimiento.RegistroPuntos;
import com.maxiconecta.crm.fidelizacion.movimiento.RegistroPuntos.DetalleMovimiento;
import com.maxiconecta.crm.fidelizacion.puntos.SaldoPuntos;
import com.maxiconecta.crm.fidelizacion.puntos.SaldoPuntosRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Saldo, nivel y últimos movimientos de puntos de un cliente en una sola consulta (SCRUM-400,
 * docs/fidelizacion/consulta-saldo-puntos.md). Un cliente sin puntos responde con {@code tienePuntos=false}
 * y un mensaje, no con un error (criterio de aceptación 2).
 * <p>
 * SCRUM-419: un saldo con la vigencia vencida que el proceso de caducidad aún no descontó se muestra como
 * {@code puntosCaducados}, con {@code saldo} (el disponible) en 0.
 */
@Service
public class ConsultaSaldoPuntos {

    public static final int MOVIMIENTOS_POR_DEFECTO = 10;
    public static final int MOVIMIENTOS_MAXIMOS = 50;
    static final String SIN_PUNTOS = "Este cliente aún no tiene puntos";

    private final SaldoPuntosRepository saldos;
    private final RegistroPuntos registro;
    private final SaldoVigente vigente;

    public ConsultaSaldoPuntos(SaldoPuntosRepository saldos, RegistroPuntos registro, SaldoVigente vigente) {
        this.saldos = saldos;
        this.registro = registro;
        this.vigente = vigente;
    }

    /** Lee el saldo y los movimientos en la misma transacción, para que la respuesta sea coherente. */
    @Transactional(readOnly = true)
    public SaldoCliente consultar(long clienteId, int movimientos) {
        if (movimientos < 1 || movimientos > MOVIMIENTOS_MAXIMOS) {
            throw new IllegalArgumentException("movimientos debe estar entre 1 y " + MOVIMIENTOS_MAXIMOS);
        }
        Optional<SaldoPuntos> saldo = saldos.findById(clienteId);
        long total = registro.contarMovimientos(clienteId);
        if (saldo.isEmpty() && total == 0) {
            return new SaldoCliente(clienteId, false, SIN_PUNTOS, 0, null, 0, null, null, null, 0, 0, List.of());
        }
        List<MovimientoReciente> ultimos = registro.ultimosMovimientos(clienteId, movimientos).stream()
                .map(MovimientoReciente::de).toList();
        SaldoVigente.Estado estado = vigente.evaluar(clienteId, saldo.map(SaldoPuntos::getSaldo).orElse(0L),
                OffsetDateTime.now(ZoneOffset.UTC));
        return new SaldoCliente(clienteId, true, null, estado.saldoDisponible(),
                saldo.map(SaldoPuntos::getNivel).orElse(null), saldo.map(SaldoPuntos::getPuntosPeriodo).orElse(0L),
                saldo.map(SaldoPuntos::getActualizadoEn).orElse(null),
                saldo.map(SaldoPuntos::getNivelActualizadoEn).orElse(null), estado.fechaCaducidad(),
                estado.puntosCaducados(), total, ultimos);
    }

    /**
     * Respuesta de la consulta; {@code mensaje} solo cuando el cliente aún no tiene puntos. {@code saldo} es el
     * disponible; {@code fechaCaducidad} y {@code puntosCaducados} siguen la regla de SCRUM-418.
     */
    public record SaldoCliente(long clienteId, boolean tienePuntos, String mensaje, long saldo, String nivel,
                               long puntosPeriodo, OffsetDateTime actualizadoEn, OffsetDateTime nivelActualizadoEn,
                               OffsetDateTime fechaCaducidad, long puntosCaducados, long totalMovimientos,
                               List<MovimientoReciente> ultimosMovimientos) {
    }

    /** Lo que el agente necesita de cada movimiento; la regla aplicada queda en el historial completo. */
    public record MovimientoReciente(String idTransaccion, String tipoMovimiento, long puntos, long variacionSaldo,
                                     long saldoResultante, String idTransaccionRevertida,
                                     OffsetDateTime fechaTransaccion, OffsetDateTime registradoEn) {

        static MovimientoReciente de(DetalleMovimiento m) {
            return new MovimientoReciente(m.idTransaccion(), m.tipoMovimiento(), m.puntos(), m.variacionSaldo(),
                    m.saldoResultante(), m.idTransaccionRevertida(), m.fechaTransaccion(), m.registradoEn());
        }
    }
}
