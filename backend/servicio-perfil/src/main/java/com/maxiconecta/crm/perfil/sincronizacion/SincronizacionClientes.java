package com.maxiconecta.crm.perfil.sincronizacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Flujo de sincronización de un mensaje de datos del cliente: primero se anota en la bitácora y
 * después se aplica al perfil en una transacción aparte. Si falla, el mensaje queda FALLIDO con
 * su causa y su contenido original, disponible para reproceso.
 */
@Service
public class SincronizacionClientes {

    private static final Logger log = LoggerFactory.getLogger(SincronizacionClientes.class);

    private final BitacoraSincronizacion bitacora;
    private final ProcesadorClientes procesador;

    public SincronizacionClientes(BitacoraSincronizacion bitacora, ProcesadorClientes procesador) {
        this.bitacora = bitacora;
        this.procesador = procesador;
    }

    public void recibir(String contenido) {
        procesar(bitacora.registrarRecepcion(contenido));
    }

    /**
     * Aplica al perfil un evento ya anotado en la bitácora. También se usa para aplicar los eventos
     * que estaban pendientes de vinculación, una vez que el administrador decide.
     */
    public EstadoEventoCliente procesar(Long idEvento) {
        try {
            ResultadoSincronizacion resultado = aplicar(idEvento);
            bitacora.cerrar(idEvento, resultado.estado(), resultado.idCliente(), resultado.causa());
            return resultado.estado();
        } catch (EventoDescartadoException ex) {
            log.info("Evento de cliente {} descartado: {}", idEvento, ex.getMessage());
            bitacora.cerrar(idEvento, EstadoEventoCliente.DESCARTADO, ex.getIdCliente(), ex.getMessage());
            return EstadoEventoCliente.DESCARTADO;
        } catch (RuntimeException ex) {
            log.warn("Evento de cliente {} fallido: {}", idEvento, causa(ex));
            bitacora.cerrar(idEvento, EstadoEventoCliente.FALLIDO, null, causa(ex));
            return EstadoEventoCliente.FALLIDO;
        }
    }

    /**
     * Si dos mensajes del mismo identificador llegan a la vez, el segundo choca con el vínculo (o la
     * vinculación pendiente) que acaba de crear el primero. Se reintenta una vez: ahora el vínculo
     * ya existe y el evento se concilia con él.
     */
    private ResultadoSincronizacion aplicar(Long idEvento) {
        try {
            return procesador.aplicar(idEvento);
        } catch (DataIntegrityViolationException ex) {
            if (!esVinculoRepetido(ex)) {
                throw ex;
            }
            log.info("Evento de cliente {}: el cliente fue dado de alta al mismo tiempo por otro mensaje; se concilia",
                    idEvento);
            return procesador.aplicar(idEvento);
        }
    }

    private static boolean esVinculoRepetido(DataIntegrityViolationException ex) {
        String mensaje = NestedExceptionUtils.getMostSpecificCause(ex).getMessage();
        return mensaje != null && (mensaje.contains("pk_cliente_origen") || mensaje.contains("pk_vinculacion_pendiente"));
    }

    /**
     * Causa para la bitácora. Los errores de lectura ya traen un mensaje claro; para los demás se
     * usa la causa más específica, que es la que explica el error.
     */
    static String causa(Throwable error) {
        Throwable raiz = error instanceof EventoIlegibleException
                ? error
                : NestedExceptionUtils.getMostSpecificCause(error);
        String mensaje = raiz.getMessage();
        return raiz.getClass().getSimpleName() + (mensaje != null ? ": " + mensaje : "");
    }
}
