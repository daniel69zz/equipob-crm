package com.maxiconecta.crm.fidelizacion.consulta;

import com.maxiconecta.crm.fidelizacion.movimiento.RegistroPuntos;
import com.maxiconecta.crm.fidelizacion.movimiento.RegistroPuntos.DetalleMovimiento;
import com.maxiconecta.crm.fidelizacion.puntos.SaldoPuntos;
import com.maxiconecta.crm.fidelizacion.puntos.SaldoPuntosRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Saldo, nivel y últimos movimientos de puntos de un cliente en una sola consulta (SCRUM-400,
 * docs/fidelizacion/consulta-saldo-puntos.md). Un cliente sin puntos responde con {@code tienePuntos=false}
 * y un mensaje, no con un error (criterio de aceptación 2).
 */
@Service
public class ConsultaSaldoPuntos {

    public static final int MOVIMIENTOS_POR_DEFECTO = 10;
    public static final int MOVIMIENTOS_MAXIMOS = 50;
    static final String SIN_PUNTOS = "Este cliente aún no tiene puntos";

    private final SaldoPuntosRepository saldos;
    private final RegistroPuntos registro;

    public ConsultaSaldoPuntos(SaldoPuntosRepository saldos, RegistroPuntos registro) {
        this.saldos = saldos;
        this.registro = registro;
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
            return new SaldoCliente(clienteId, false, SIN_PUNTOS, 0, null, 0, null, null, 0, List.of());
        }
        List<MovimientoReciente> ultimos = registro.ultimosMovimientos(clienteId, movimientos).stream()
                .map(MovimientoReciente::de).toList();
        return new SaldoCliente(clienteId, true, null, saldo.map(SaldoPuntos::getSaldo).orElse(0L),
                saldo.map(SaldoPuntos::getNivel).orElse(null), saldo.map(SaldoPuntos::getPuntosPeriodo).orElse(0L),
                saldo.map(SaldoPuntos::getActualizadoEn).orElse(null),
                saldo.map(SaldoPuntos::getNivelActualizadoEn).orElse(null), total, ultimos);
    }

    /** Respuesta de la consulta; {@code mensaje} solo cuando el cliente aún no tiene puntos. */
    public record SaldoCliente(long clienteId, boolean tienePuntos, String mensaje, long saldo, String nivel,
                               long puntosPeriodo, OffsetDateTime actualizadoEn, OffsetDateTime nivelActualizadoEn,
                               long totalMovimientos, List<MovimientoReciente> ultimosMovimientos) {
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
