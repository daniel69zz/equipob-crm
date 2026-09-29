package com.maxiconecta.crm.comportamiento.ingesta;

import com.maxiconecta.crm.comportamiento.validacion.EventoInvalidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Service;

/**
 * Flujo de ingesta de un mensaje de venta (compra confirmada o anulación): primero se anota en la
 * bitácora y después se procesa en una transacción aparte. Si el procesamiento falla, el mensaje
 * queda FALLIDO con su causa y su contenido original, disponible para reproceso.
 * <p>
 * Cada mensaje se procesa según el {@code tipoEvento} que declara. Si no lo trae (por ejemplo,
 * porque no es JSON), según la cola por la que llegó; al reprocesarlo, como compra.
 */
@Service
public class IngestaCompras {

    private static final Logger log = LoggerFactory.getLogger(IngestaCompras.class);

    private final BitacoraIngesta bitacora;
    private final ProcesadorCompras procesador;
    private final ProcesadorAnulaciones procesadorAnulaciones;

    public IngestaCompras(BitacoraIngesta bitacora, ProcesadorCompras procesador,
                          ProcesadorAnulaciones procesadorAnulaciones) {
        this.bitacora = bitacora;
        this.procesador = procesador;
        this.procesadorAnulaciones = procesadorAnulaciones;
    }

    /** Mensaje recibido por la cola de compras. */
    public void recibir(String contenido) {
        Long idEvento = bitacora.registrarRecepcion(contenido);
        procesar(idEvento, false);
    }

    /** Mensaje recibido por la cola de anulaciones. */
    public void recibirAnulacion(String contenido) {
        Long idEvento = bitacora.registrarRecepcion(contenido);
        procesar(idEvento, true);
    }

    /**
     * Procesa un evento que ya existe en la bitácora. Lo usan tanto la recepción normal como
     * el reproceso administrativo para mantener un único tratamiento de resultados y errores.
     */
    public ResultadoProcesamiento procesarRegistrado(Long idEvento) {
        return procesar(idEvento, false);
    }

    private ResultadoProcesamiento procesar(Long idEvento, boolean anulacionSiNoDeclaraTipo) {
        try {
            if (esAnulacion(bitacora.tipoEvento(idEvento), anulacionSiNoDeclaraTipo)) {
                procesadorAnulaciones.procesar(idEvento);
            } else {
                procesador.procesar(idEvento);
            }
            return new ResultadoProcesamiento(EstadoEvento.PROCESADO, null);
        } catch (EventoDuplicadoException ex) {
            log.info("Evento {} descartado por duplicado: {}", idEvento, ex.getMessage());
            bitacora.marcarDescartado(idEvento, ex.getMessage());
            return new ResultadoProcesamiento(EstadoEvento.DESCARTADO, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Evento {} fallido: {}", idEvento, causa(ex));
            String causa = causa(ex);
            bitacora.marcarFallido(idEvento, causa);
            return new ResultadoProcesamiento(EstadoEvento.FALLIDO, causa);
        }
    }

    private static boolean esAnulacion(String tipoEvento, boolean siNoDeclaraTipo) {
        if (EventoAnulacionCompra.TIPO.equals(tipoEvento)) {
            return true;
        }
        return siNoDeclaraTipo && !EventoCompraConfirmada.TIPO.equals(tipoEvento);
    }

    /**
     * Causa para la bitácora. Los errores de lectura y de validación ya traen un mensaje claro;
     * para los demás se usa la causa más específica, que es la que explica el error.
     */
    static String causa(Throwable error) {
        Throwable raiz = error instanceof EventoIlegibleException || error instanceof EventoInvalidoException
                || error instanceof AnulacionInconsistenteException
                ? error
                : NestedExceptionUtils.getMostSpecificCause(error);
        String mensaje = raiz.getMessage();
        return raiz.getClass().getSimpleName() + (mensaje != null ? ": " + mensaje : "");
    }

    public record ResultadoProcesamiento(EstadoEvento estado, String causa) {
    }
}
