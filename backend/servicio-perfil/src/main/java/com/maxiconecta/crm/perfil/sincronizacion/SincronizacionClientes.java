package com.maxiconecta.crm.perfil.sincronizacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
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
        Long idEvento = bitacora.registrarRecepcion(contenido);
        try {
            ResultadoSincronizacion resultado = procesador.aplicar(idEvento);
            bitacora.cerrar(idEvento, resultado.estado(), resultado.idCliente(), resultado.causa());
        } catch (EventoDescartadoException ex) {
            log.info("Evento de cliente {} descartado: {}", idEvento, ex.getMessage());
            bitacora.cerrar(idEvento, EstadoEventoCliente.DESCARTADO, ex.getIdCliente(), ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Evento de cliente {} fallido: {}", idEvento, causa(ex));
            bitacora.cerrar(idEvento, EstadoEventoCliente.FALLIDO, null, causa(ex));
        }
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
