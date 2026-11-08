package com.maxiconecta.crm.fidelizacion.movimiento;

import com.maxiconecta.crm.fidelizacion.caducidad.ProcesoCaducidad;
import com.maxiconecta.crm.fidelizacion.nivel.AsignacionNiveles;
import com.maxiconecta.crm.fidelizacion.regla.ReglaAcumulacion;
import com.maxiconecta.crm.fidelizacion.regla.ReglasAcumulacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Aplica un movimiento ya anotado en la bitácora: acumula los puntos de una compra (SCRUM-387),
 * descuenta los de una redención (SCRUM-404) o revierte los de una compra anulada o devuelta
 * (SCRUM-540). Todo ocurre en una transacción: el detalle, el saldo, el nivel y el estado PROCESADO se
 * guardan juntos o no se guarda nada, así que un evento inválido, una redención sin saldo suficiente o un
 * error no cambian el saldo.
 * <p>
 * SCRUM-419: antes de aplicar el movimiento, si el saldo del cliente ya estaba vencido en la fecha del
 * movimiento, se caduca en la misma transacción (SCRUM-50): una compra posterior no renueva los puntos vencidos
 * y una redención posterior no los puede usar.
 */
@Service
public class ProcesadorMovimientos {

    private final BitacoraMovimientos bitacora;
    private final LectorMovimientos lector;
    private final ClientesPerfil perfiles;
    private final ReglasAcumulacion reglas;
    private final RegistroPuntos registro;
    private final AsignacionNiveles niveles;
    private final ProcesoCaducidad caducidad;

    public ProcesadorMovimientos(BitacoraMovimientos bitacora, LectorMovimientos lector, ClientesPerfil perfiles,
                                 ReglasAcumulacion reglas, RegistroPuntos registro, AsignacionNiveles niveles,
                                 ProcesoCaducidad caducidad) {
        this.bitacora = bitacora;
        this.lector = lector;
        this.perfiles = perfiles;
        this.reglas = reglas;
        this.registro = registro;
        this.niveles = niveles;
        this.caducidad = caducidad;
    }

    /** @return el saldo resultante del cliente */
    @Transactional
    public long procesar(long idEventoMovimiento, String contenido) {
        EventoMovimientoPuntos.Movimiento movimiento = lector.leer(contenido).movimiento();
        TipoMovimiento tipo = LectorMovimientos.tipo(movimiento.tipoMovimiento());
        long clienteId = perfiles.buscar(movimiento.idCliente())
                .orElseThrow(() -> new EventoInvalidoException("El cliente " + movimiento.idCliente()
                        + " no tiene perfil en el CRM; el movimiento queda para reproceso"));
        Optional<ProcesoCaducidad.Caducidad> caducada = caducidad.caducarSiVencio(clienteId, movimiento.fecha());
        long saldo = switch (tipo) {
            case ACUMULACION -> acumular(clienteId, movimiento, idEventoMovimiento);
            case REDENCION -> redimir(clienteId, movimiento, idEventoMovimiento, caducada);
            case REVERSA -> revertir(clienteId, movimiento, idEventoMovimiento);
            // LectorMovimientos ya la rechaza: la caducidad no llega por eventos (SCRUM-417).
            case CADUCIDAD, AJUSTE -> throw new EventoInvalidoException("movimiento.tipoMovimiento " + tipo
                    + " no lo puede notificar Ventas");
        };
        registro.anotarSaldoResultante(movimiento.idTransaccion(), tipo, saldo);
        // SCRUM-49: con el saldo cambia también el nivel, en la misma transacción (criterio 4 de SCRUM-48 y
        // de SCRUM-524); si cambia, queda registrado con este movimiento como origen (SCRUM-541).
        niveles.recalcular(clienteId, movimiento.idTransaccion(), tipo.name());
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
    private long redimir(long clienteId, EventoMovimientoPuntos.Movimiento movimiento, long idEventoMovimiento,
                         Optional<ProcesoCaducidad.Caducidad> caducada) {
        long puntos = movimiento.puntos().longValueExact();
        registro.reservar(movimiento.idTransaccion(), TipoMovimiento.REDENCION, clienteId, movimiento.idCliente(),
                movimiento.valorConsumido(), puntos, null, movimiento.fecha(), idEventoMovimiento);
        try {
            return registro.debitar(clienteId, movimiento.idTransaccion(), puntos);
        } catch (SaldoInsuficienteException ex) {
            // SCRUM-419: la causa indica que el saldo no alcanza porque sus puntos caducaron antes de la redención.
            if (caducada.isEmpty()) {
                throw ex;
            }
            throw new SaldoInsuficienteException(movimiento.idTransaccion(), ex.solicitados(), ex.disponibles(),
                    "; " + caducada.get().puntos() + " puntos caducaron el " + caducada.get().fechaCaducidad()
                            + ", antes de la redención");
        }
    }

    /**
     * SCRUM-540: la reversa apunta a la acumulación de la compra anulada o devuelta. Se reserva (una
     * reversa reentregada queda DESCARTADO, criterio 3), se calculan sus puntos con la regla de esa compra
     * (SCRUM-538) y se descuentan hasta donde alcance el saldo.
     */
    private long revertir(long clienteId, EventoMovimientoPuntos.Movimiento movimiento, long idEventoMovimiento) {
        String idOriginal = movimiento.idTransaccionOriginal();
        ReglaReversa.Original original = registro.acumulacionParaRevertir(idOriginal)
                .orElseThrow(() -> new EventoInvalidoException("La transacción " + idOriginal + " no tiene una "
                        + "acumulación de puntos registrada; la reversa " + movimiento.idTransaccion()
                        + " queda para reproceso"));
        if (original.clienteId() != clienteId) {
            throw new EventoInvalidoException("El cliente " + movimiento.idCliente() + " no es el de la transacción "
                    + idOriginal);
        }
        registro.reservarReversa(movimiento.idTransaccion(), clienteId, movimiento.idCliente(),
                movimiento.valorConsumido(), original, movimiento.fecha(), idEventoMovimiento);
        RegistroPuntos.Revertido antes = registro.revertidoAntes(idOriginal, movimiento.idTransaccion());
        long puntos = ReglaReversa.puntosARevertir(original, antes.monto(), antes.puntos(), movimiento.idTransaccion(),
                movimiento.valorConsumido());
        // SCRUM-414: si los puntos de la compra ya caducaron, no hay nada que descontar; todos quedan como no
        // recuperados y la reversa no le quita puntos a compras posteriores.
        RegistroPuntos.Descuento descuento = caducidad.caducaronLosPuntosDe(idOriginal)
                ? new RegistroPuntos.Descuento(registro.saldoDisponible(clienteId), 0)
                : registro.descontarHastaCero(clienteId, puntos);
        registro.completarReversa(movimiento.idTransaccion(), puntos, puntos - descuento.descontados());
        return descuento.saldo();
    }
}
