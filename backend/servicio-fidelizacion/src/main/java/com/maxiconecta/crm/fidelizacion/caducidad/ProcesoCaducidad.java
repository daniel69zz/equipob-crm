package com.maxiconecta.crm.fidelizacion.caducidad;

import com.maxiconecta.crm.fidelizacion.movimiento.TipoMovimiento;
import com.maxiconecta.crm.fidelizacion.nivel.AsignacionNiveles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Caducidad de los puntos de un cliente (SCRUM-50, docs/fidelizacion/caducidad-puntos.md). Si su saldo ya estaba
 * vencido en el momento evaluado, registra el movimiento de caducidad (SCRUM-417), deja el saldo en 0
 * (SCRUM-414) y recalcula el nivel con la caducidad como origen.
 * <p>
 * El proceso automático (SCRUM-415) revisa cada día a los clientes con saldo vencido, uno por transacción: el
 * error de un cliente no deshace lo de los demás, y vuelve a intentarse en la ejecución siguiente.
 */
@Service
public class ProcesoCaducidad {

    private static final Logger log = LoggerFactory.getLogger(ProcesoCaducidad.class);

    private final RegistroCaducidad registro;
    private final VigenciaPuntos vigencia;
    private final AsignacionNiveles niveles;
    private final TransactionTemplate porCliente;

    public ProcesoCaducidad(RegistroCaducidad registro, VigenciaPuntos vigencia, AsignacionNiveles niveles,
                            PlatformTransactionManager transacciones) {
        this.registro = registro;
        this.vigencia = vigencia;
        this.niveles = niveles;
        this.porCliente = new TransactionTemplate(transacciones);
    }

    /**
     * SCRUM-415: caduca los saldos vencidos hasta ahora. Se puede ejecutar varias veces el mismo día: un saldo ya
     * caducado queda en 0 y su vencimiento ya está registrado, así que no se descuenta otra vez (criterio 3).
     */
    public Ejecucion caducarVencidos() {
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
        List<Long> candidatos = registro.candidatos(ahora, vigencia.meses());
        int caducados = 0;
        int conError = 0;
        long puntos = 0;
        for (long clienteId : candidatos) {
            try {
                Optional<Caducidad> caducidad = porCliente.execute(estado -> caducarSiVencio(clienteId, ahora));
                if (caducidad != null && caducidad.isPresent()) {
                    caducados++;
                    puntos += caducidad.get().puntos();
                }
            } catch (RuntimeException ex) {
                conError++;
                log.warn("No se pudo caducar los puntos del cliente {}; se reintentará en la próxima ejecución: {}",
                        clienteId, ex.getMessage());
            }
        }
        log.info("Caducidad de puntos: {} clientes revisados, {} con puntos caducados ({} puntos), {} con error",
                candidatos.size(), caducados, puntos, conError);
        return new Ejecucion(ahora, vigencia.meses(), candidatos.size(), caducados, puntos, conError);
    }

    /**
     * Caduca el saldo del cliente si ya estaba vencido en {@code momento}. Corre dentro de la transacción de quien
     * la pide: el saldo queda bloqueado hasta su fin.
     *
     * @return la caducidad aplicada, o vacío si el saldo sigue vigente, ya es 0 o ese vencimiento ya se aplicó
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Caducidad> caducarSiVencio(long clienteId, OffsetDateTime momento) {
        Optional<Long> saldo = registro.bloquearSaldo(clienteId);
        if (saldo.isEmpty() || saldo.get() <= 0) {
            return Optional.empty();
        }
        Optional<RegistroCaducidad.UltimaCompra> compra = registro.ultimaCompra(clienteId);
        if (compra.isEmpty()) {
            return Optional.empty();
        }
        OffsetDateTime fechaCaducidad = ReglaCaducidad.fechaCaducidad(compra.get().fecha(), vigencia.meses());
        if (!ReglaCaducidad.vencida(fechaCaducidad, momento)) {
            return Optional.empty();
        }
        String id = ReglaCaducidad.idMovimiento(clienteId, fechaCaducidad);
        long puntos = saldo.get();
        if (!registro.registrar(id, clienteId, compra.get().idClienteOrigen(), puntos, fechaCaducidad)) {
            return Optional.empty();
        }
        registro.vaciarSaldo(clienteId);
        niveles.recalcular(clienteId, id, TipoMovimiento.CADUCIDAD.name());
        log.info("Caducaron {} puntos del cliente {} (vencieron el {})", puntos, clienteId, fechaCaducidad);
        return Optional.of(new Caducidad(clienteId, id, puntos, fechaCaducidad));
    }

    /**
     * SCRUM-414: si los puntos de la compra ya caducaron, una reversa de esa compra no tiene nada que descontar: no
     * le quita puntos a compras posteriores.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean caducaronLosPuntosDe(String idCompra) {
        return registro.caducaronLosPuntosDe(idCompra);
    }

    /** Resultado de una ejecución del proceso de caducidad. */
    public record Ejecucion(OffsetDateTime ejecutadoEn, int vigenciaMeses, int clientesRevisados,
                            int clientesConPuntosCaducados, long puntosCaducados, int clientesConError) {
    }

    /** Caducidad aplicada a un cliente. */
    public record Caducidad(long clienteId, String idTransaccion, long puntos, OffsetDateTime fechaCaducidad) {
    }
}
