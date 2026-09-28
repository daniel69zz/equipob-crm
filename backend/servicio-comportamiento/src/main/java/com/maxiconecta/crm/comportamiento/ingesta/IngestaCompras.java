package com.maxiconecta.crm.comportamiento.ingesta;

import com.maxiconecta.crm.comportamiento.validacion.EventoInvalidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Service;

/**
 * Flujo de ingesta de un mensaje de compra confirmada: primero se anota en la bitácora y
 * después se procesa en una transacción aparte. Si el procesamiento falla, el mensaje queda
 * FALLIDO con su causa y su contenido original, disponible para reproceso.
 */
@Service
public class IngestaCompras {

    private static final Logger log = LoggerFactory.getLogger(IngestaCompras.class);

    private final BitacoraIngesta bitacora;
    private final ProcesadorCompras procesador;

    public IngestaCompras(BitacoraIngesta bitacora, ProcesadorCompras procesador) {
        this.bitacora = bitacora;
        this.procesador = procesador;
    }

    public void recibir(String contenido) {
        Long idEvento = bitacora.registrarRecepcion(contenido);
        try {
            procesador.procesar(idEvento);
        } catch (CompraDuplicadaException ex) {
            log.info("Evento {} descartado: {}", idEvento, ex.getMessage());
            bitacora.marcarDescartado(idEvento, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Evento {} fallido: {}", idEvento, causa(ex));
            bitacora.marcarFallido(idEvento, causa(ex));
        }
    }

    /**
     * Causa para la bitácora. Los errores de lectura y de validación ya traen un mensaje claro;
     * para los demás se usa la causa más específica, que es la que explica el error.
     */
    static String causa(Throwable error) {
        Throwable raiz = error instanceof EventoIlegibleException || error instanceof EventoInvalidoException
                ? error
                : NestedExceptionUtils.getMostSpecificCause(error);
        String mensaje = raiz.getMessage();
        return raiz.getClass().getSimpleName() + (mensaje != null ? ": " + mensaje : "");
    }
}
