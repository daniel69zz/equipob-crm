package com.maxiconecta.crm.comportamiento.ingesta;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * Registro de las transacciones ya procesadas, común a compras y anulaciones
 * (docs/ingesta/idempotencia-eventos-venta.md). Se usa dentro de la transacción del procesamiento:
 * la clave se guarda junto con la compra o la anulación, o no se guarda.
 */
@Component
public class ControlIdempotencia {

    private final EventoProcesadoRepository procesados;

    public ControlIdempotencia(EventoProcesadoRepository procesados) {
        this.procesados = procesados;
    }

    /**
     * Reserva la clave para el evento. La clave se envía a la base de inmediato: si otra copia del
     * mismo evento se está procesando a la vez, la llave primaria detiene a una de las dos antes
     * de que escriba en el historial.
     *
     * @param duplicado construye la excepción a lanzar a partir del motivo del descarte
     * @throws EventoDuplicadoException si la transacción ya fue procesada
     */
    public void reservar(ClaveIdempotencia clave, Long idEventoRecibido,
                         Function<String, ? extends EventoDuplicadoException> duplicado) {
        procesados.buscar(clave).ifPresent(previo -> {
            throw duplicado.apply("ya fue registrada por el evento " + previo.getIdEventoRecibido());
        });
        try {
            procesados.saveAndFlush(new EventoProcesado(clave, idEventoRecibido));
        } catch (DataIntegrityViolationException ex) {
            if (!esClaveRepetida(ex)) {
                throw ex;
            }
            throw duplicado.apply("ya fue registrada por otro mensaje procesado al mismo tiempo");
        }
    }

    private static boolean esClaveRepetida(DataIntegrityViolationException ex) {
        String mensaje = NestedExceptionUtils.getMostSpecificCause(ex).getMessage();
        return mensaje != null && mensaje.contains("pk_evento_procesado");
    }
}
