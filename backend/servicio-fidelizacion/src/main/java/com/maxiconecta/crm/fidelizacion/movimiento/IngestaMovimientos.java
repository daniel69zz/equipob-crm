package com.maxiconecta.crm.fidelizacion.movimiento;

import com.maxiconecta.crm.fidelizacion.movimiento.BitacoraMovimientos.Estado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Service;

/**
 * Flujo de un movimiento RIO-CRM-03: primero se anota en la bitácora y después se procesa en una
 * transacción aparte. El resultado queda PROCESADO, DESCARTADO (transacción ya acreditada) o FALLIDO
 * con su causa y su contenido, disponible para reproceso.
 */
@Service
public class IngestaMovimientos {

    private static final Logger log = LoggerFactory.getLogger(IngestaMovimientos.class);

    private final BitacoraMovimientos bitacora;
    private final LectorMovimientos lector;
    private final ProcesadorMovimientos procesador;

    public IngestaMovimientos(BitacoraMovimientos bitacora, LectorMovimientos lector, ProcesadorMovimientos procesador) {
        this.bitacora = bitacora;
        this.lector = lector;
        this.procesador = procesador;
    }

    /** Mensaje recibido por la cola (RabbitMQ o Pub/Sub). */
    public Estado recibir(String contenido) {
        long id = bitacora.registrar(contenido, lector.cabecera(contenido));
        return procesar(id, contenido);
    }

    /** Reproceso administrativo: solo de un evento FALLIDO. */
    public BitacoraMovimientos.EventoMovimiento reprocesar(long id) {
        BitacoraMovimientos.EventoMovimiento evento = bitacora.obtener(id)
                .orElseThrow(() -> new EventoNoEncontradoException("No existe el evento " + id + " en la bitácora"));
        if (evento.estado() != Estado.FALLIDO) {
            throw new EventoNoReprocesableException("Solo se reprocesan eventos FALLIDO; el evento " + id + " está "
                    + evento.estado());
        }
        bitacora.contarIntento(id);
        procesar(id, evento.contenido());
        return bitacora.obtener(id).orElseThrow();
    }

    private Estado procesar(long id, String contenido) {
        try {
            long saldo = procesador.procesar(id, contenido);
            log.info("Movimiento {} acreditado; saldo resultante {}", id, saldo);
            return Estado.PROCESADO;
        } catch (MovimientoDuplicadoException ex) {
            log.info("Movimiento {} descartado: {}", id, ex.getMessage());
            bitacora.marcar(id, Estado.DESCARTADO, ex.getMessage());
            return Estado.DESCARTADO;
        } catch (RuntimeException ex) {
            String causa = causa(ex);
            log.warn("Movimiento {} fallido: {}", id, causa);
            bitacora.marcar(id, Estado.FALLIDO, causa);
            return Estado.FALLIDO;
        }
    }

    static String causa(Throwable error) {
        Throwable raiz = error instanceof EventoInvalidoException || error instanceof ClientesPerfil.PerfilNoDisponibleException
                ? error : NestedExceptionUtils.getMostSpecificCause(error);
        String mensaje = raiz.getMessage();
        return raiz.getClass().getSimpleName() + (mensaje != null ? ": " + mensaje : "");
    }

    public static class EventoNoEncontradoException extends RuntimeException {
        public EventoNoEncontradoException(String mensaje) {
            super(mensaje);
        }
    }

    public static class EventoNoReprocesableException extends RuntimeException {
        public EventoNoReprocesableException(String mensaje) {
            super(mensaje);
        }
    }
}
