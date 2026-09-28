package com.maxiconecta.crm.perfil.sincronizacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.maxiconecta.crm.perfil.configuracion.PoliticaReintentos;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.time.Duration;
import java.util.List;

/**
 * Flujo de sincronización de un mensaje de datos del cliente: primero se anota en la bitácora y
 * después se aplica al perfil en una transacción aparte. Si falla, el mensaje queda FALLIDO con
 * su causa y su contenido original, disponible para reproceso.
 */
@Service
public class SincronizacionClientes {

    private static final Logger log = LoggerFactory.getLogger(SincronizacionClientes.class);

    /** Conexión (08), serialización (40001), interbloqueo (40P01), base reiniciándose (57P01), sin conexiones (53300). */
    private static final List<String> ESTADOS_SQL_TRANSITORIOS = List.of("08", "40001", "40P01", "57P01", "53300");

    private final BitacoraSincronizacion bitacora;
    private final ProcesadorClientes procesador;
    private final PoliticaReintentos reintentos;

    public SincronizacionClientes(BitacoraSincronizacion bitacora, ProcesadorClientes procesador,
                                  PoliticaReintentos reintentos) {
        this.bitacora = bitacora;
        this.procesador = procesador;
        this.reintentos = reintentos;
    }

    public void recibir(String contenido) {
        procesar(bitacora.registrarRecepcion(contenido));
    }

    /**
     * Aplica al perfil un evento ya anotado en la bitácora. También se usa para aplicar los eventos
     * que estaban pendientes de vinculación, una vez que el administrador decide.
     * <p>
     * Un error técnico transitorio (la base no responde, se cortó la conexión, un bloqueo) se
     * reintenta según {@link PoliticaReintentos}; un error del propio mensaje no. El resultado final
     * queda en la bitácora con la cantidad de intentos.
     */
    public EstadoEventoCliente procesar(Long idEvento) {
        for (int intento = 1; ; intento++) {
            try {
                ResultadoSincronizacion resultado = aplicar(idEvento);
                bitacora.cerrar(idEvento, resultado.estado(), resultado.idCliente(), resultado.causa(), intento);
                return resultado.estado();
            } catch (EventoDescartadoException ex) {
                log.info("Evento de cliente {} descartado: {}", idEvento, ex.getMessage());
                bitacora.cerrar(idEvento, EstadoEventoCliente.DESCARTADO, ex.getIdCliente(), ex.getMessage(), intento);
                return EstadoEventoCliente.DESCARTADO;
            } catch (RuntimeException ex) {
                boolean tecnico = esErrorTecnicoTransitorio(ex);
                if (tecnico && intento < reintentos.maximo() && esperar(reintentos.esperaTras(intento))) {
                    log.warn("Evento de cliente {}: error técnico en el intento {} de {}, se reintenta: {}", idEvento,
                            intento, reintentos.maximo(), causa(ex));
                    continue;
                }
                String causa = tecnico ? "Error técnico tras " + intento + " intento(s): " + causa(ex) : causa(ex);
                log.warn("Evento de cliente {} fallido: {}", idEvento, causa);
                bitacora.cerrar(idEvento, EstadoEventoCliente.FALLIDO, null, causa, intento);
                return EstadoEventoCliente.FALLIDO;
            }
        }
    }

    /**
     * Errores de infraestructura que pueden resolverse solos al reintentar. Se revisa toda la cadena
     * de causas, porque el error de la base suele llegar envuelto.
     */
    static boolean esErrorTecnicoTransitorio(Throwable error) {
        for (Throwable actual = error; actual != null; actual = actual.getCause() == actual ? null : actual.getCause()) {
            if (actual instanceof TransientDataAccessException || actual instanceof RecoverableDataAccessException
                    || actual instanceof DataAccessResourceFailureException
                    || actual instanceof CannotCreateTransactionException
                    || actual instanceof SQLTransientException || actual instanceof SQLRecoverableException) {
                return true;
            }
            if (actual instanceof SQLException sql && sql.getSQLState() != null
                    && ESTADOS_SQL_TRANSITORIOS.stream().anyMatch(sql.getSQLState()::startsWith)) {
                return true;
            }
        }
        return false;
    }

    /** @return false si el hilo fue interrumpido: en ese caso no se sigue reintentando */
    private static boolean esperar(Duration espera) {
        try {
            Thread.sleep(espera.toMillis());
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
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
