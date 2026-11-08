package com.maxiconecta.crm.fidelizacion.caducidad;

import com.maxiconecta.crm.fidelizacion.movimiento.TipoMovimiento;
import com.maxiconecta.crm.fidelizacion.nivel.AsignacionNiveles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Caducidad de los puntos de un cliente (SCRUM-50, docs/fidelizacion/caducidad-puntos.md). Si su saldo ya estaba
 * vencido en el momento evaluado, registra el movimiento de caducidad (SCRUM-417), deja el saldo en 0
 * (SCRUM-414) y recalcula el nivel con la caducidad como origen.
 */
@Service
public class ProcesoCaducidad {

    private static final Logger log = LoggerFactory.getLogger(ProcesoCaducidad.class);

    private final RegistroCaducidad registro;
    private final VigenciaPuntos vigencia;
    private final AsignacionNiveles niveles;

    public ProcesoCaducidad(RegistroCaducidad registro, VigenciaPuntos vigencia, AsignacionNiveles niveles) {
        this.registro = registro;
        this.vigencia = vigencia;
        this.niveles = niveles;
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

    /** Caducidad aplicada a un cliente. */
    public record Caducidad(long clienteId, String idTransaccion, long puntos, OffsetDateTime fechaCaducidad) {
    }
}
