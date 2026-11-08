package com.maxiconecta.crm.fidelizacion.movimiento;

import com.maxiconecta.crm.fidelizacion.regla.ReglaAcumulacion;
import com.maxiconecta.crm.fidelizacion.regla.ReglasAcumulacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acumula los puntos de un movimiento ya anotado en la bitácora (SCRUM-387). Todo ocurre en una
 * transacción: el detalle, el saldo y el estado PROCESADO se guardan juntos o no se guarda nada, así
 * que un evento inválido o un error no cambian el saldo (criterio de aceptación 3).
 */
@Service
public class ProcesadorMovimientos {

    private final BitacoraMovimientos bitacora;
    private final LectorMovimientos lector;
    private final ClientesPerfil perfiles;
    private final ReglasAcumulacion reglas;
    private final RegistroPuntos registro;

    public ProcesadorMovimientos(BitacoraMovimientos bitacora, LectorMovimientos lector, ClientesPerfil perfiles,
                                 ReglasAcumulacion reglas, RegistroPuntos registro) {
        this.bitacora = bitacora;
        this.lector = lector;
        this.perfiles = perfiles;
        this.reglas = reglas;
        this.registro = registro;
    }

    @Transactional
    public long procesar(long idEventoMovimiento, String contenido) {
        EventoMovimientoPuntos.Movimiento movimiento = lector.leer(contenido).movimiento();
        TipoMovimiento tipo = LectorMovimientos.tipo(movimiento.tipoMovimiento());
        if (tipo != TipoMovimiento.ACUMULACION) {
            throw new EventoInvalidoException("El tipo de movimiento " + tipo + " todavía no se procesa ("
                    + (tipo == TipoMovimiento.REDENCION ? "SCRUM-48" : "SCRUM-524") + "); queda para reproceso");
        }
        long clienteId = perfiles.buscar(movimiento.idCliente())
                .orElseThrow(() -> new EventoInvalidoException("El cliente " + movimiento.idCliente()
                        + " no tiene perfil en el CRM; el movimiento queda para reproceso"));
        // SCRUM-46: la regla vigente al procesar el movimiento; su versión queda en el detalle.
        ReglaAcumulacion regla = reglas.vigente();
        long puntos = regla.puntos(movimiento.valorConsumido());

        registro.reservar(movimiento.idTransaccion(), tipo, clienteId, movimiento.idCliente(),
                movimiento.valorConsumido(), puntos, regla, movimiento.fecha(), idEventoMovimiento);
        long saldo = registro.acreditar(clienteId, puntos);
        registro.anotarSaldoResultante(movimiento.idTransaccion(), tipo, saldo);
        bitacora.marcarProcesado(idEventoMovimiento);
        return saldo;
    }
}
