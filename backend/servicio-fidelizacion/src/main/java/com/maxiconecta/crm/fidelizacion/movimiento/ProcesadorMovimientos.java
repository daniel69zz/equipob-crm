package com.maxiconecta.crm.fidelizacion.movimiento;

import com.maxiconecta.crm.fidelizacion.nivel.AsignacionNiveles;
import com.maxiconecta.crm.fidelizacion.regla.ReglaAcumulacion;
import com.maxiconecta.crm.fidelizacion.regla.ReglasAcumulacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aplica un movimiento ya anotado en la bitácora: acumula los puntos de una compra (SCRUM-387) o
 * descuenta los de una redención (SCRUM-404). Todo ocurre en una transacción: el detalle, el saldo, el
 * nivel y el estado PROCESADO se guardan juntos o no se guarda nada, así que un evento inválido, una
 * redención sin saldo suficiente o un error no cambian el saldo.
 */
@Service
public class ProcesadorMovimientos {

    private final BitacoraMovimientos bitacora;
    private final LectorMovimientos lector;
    private final ClientesPerfil perfiles;
    private final ReglasAcumulacion reglas;
    private final RegistroPuntos registro;
    private final AsignacionNiveles niveles;

    public ProcesadorMovimientos(BitacoraMovimientos bitacora, LectorMovimientos lector, ClientesPerfil perfiles,
                                 ReglasAcumulacion reglas, RegistroPuntos registro, AsignacionNiveles niveles) {
        this.bitacora = bitacora;
        this.lector = lector;
        this.perfiles = perfiles;
        this.reglas = reglas;
        this.registro = registro;
        this.niveles = niveles;
    }

    /** @return el saldo resultante del cliente */
    @Transactional
    public long procesar(long idEventoMovimiento, String contenido) {
        EventoMovimientoPuntos.Movimiento movimiento = lector.leer(contenido).movimiento();
        TipoMovimiento tipo = LectorMovimientos.tipo(movimiento.tipoMovimiento());
        if (tipo == TipoMovimiento.REVERSA) {
            throw new EventoInvalidoException("El tipo de movimiento REVERSA todavía no se procesa (SCRUM-524); "
                    + "queda para reproceso");
        }
        long clienteId = perfiles.buscar(movimiento.idCliente())
                .orElseThrow(() -> new EventoInvalidoException("El cliente " + movimiento.idCliente()
                        + " no tiene perfil en el CRM; el movimiento queda para reproceso"));
        long saldo = tipo == TipoMovimiento.REDENCION
                ? redimir(clienteId, movimiento, idEventoMovimiento)
                : acumular(clienteId, movimiento, idEventoMovimiento);
        registro.anotarSaldoResultante(movimiento.idTransaccion(), tipo, saldo);
        // SCRUM-49: con el saldo cambia también el nivel, en la misma transacción (criterio 4 de SCRUM-48).
        niveles.recalcular(clienteId);
        bitacora.marcarProcesado(idEventoMovimiento);
        return saldo;
    }

    private long acumular(long clienteId, EventoMovimientoPuntos.Movimiento movimiento, long idEventoMovimiento) {
        // SCRUM-46: la regla vigente al procesar el movimiento; su versión queda en el detalle.
        ReglaAcumulacion regla = reglas.vigente();
        long puntos = regla.puntos(movimiento.valorConsumido());
        registro.reservar(movimiento.idTransaccion(), TipoMovimiento.ACUMULACION, clienteId, movimiento.idCliente(),
                movimiento.valorConsumido(), puntos, regla, movimiento.fecha(), idEventoMovimiento);
        return registro.acreditar(clienteId, puntos);
    }

    /**
     * SCRUM-404: primero se reserva la transacción (una reentrega queda DESCARTADO, criterio 3) y después
     * se descuentan los puntos solo si el saldo alcanza (SCRUM-407, criterio 2). No se aplica la regla de
     * acumulación: los puntos los decide Marketplace y Ventas.
     */
    private long redimir(long clienteId, EventoMovimientoPuntos.Movimiento movimiento, long idEventoMovimiento) {
        long puntos = movimiento.puntos().longValueExact();
        registro.reservar(movimiento.idTransaccion(), TipoMovimiento.REDENCION, clienteId, movimiento.idCliente(),
                movimiento.valorConsumido(), puntos, null, movimiento.fecha(), idEventoMovimiento);
        return registro.debitar(clienteId, movimiento.idTransaccion(), puntos);
    }
}
