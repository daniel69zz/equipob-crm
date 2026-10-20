package com.maxiconecta.crm.comportamiento.riesgo;

import com.maxiconecta.crm.comportamiento.cambio.CriterioCambioComportamiento;
import com.maxiconecta.crm.comportamiento.riesgo.AgregadoRiesgoClientes.ComprasCliente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Calcula y mantiene el riesgo de abandono de los clientes (SCRUM-322, docs/compra/riesgo-abandono.md)
 * con la fórmula de {@link CriterioRiesgoAbandono}. {@link #recalcularTodos()} lo hace para todos de
 * forma programada ({@link RiesgoAbandonoScheduler}); {@link #recalcular(long)} corrige a un cliente
 * en cuanto cambian sus compras ({@link RecalculoRiesgoPorCompra}). Un cliente que entra en riesgo
 * ALTO queda marcado para seguimiento comercial (SCRUM-326).
 */
@Service
public class CalculadoraRiesgoAbandono {

    private static final Logger log = LoggerFactory.getLogger(CalculadoraRiesgoAbandono.class);

    private final AgregadoRiesgoClientes agregado;
    private final CriterioRiesgoAbandono criterio;
    private final CriterioCambioComportamiento cambios;
    private final RiesgoAbandonoClienteRepository riesgos;
    private final Clock reloj;

    public CalculadoraRiesgoAbandono(AgregadoRiesgoClientes agregado, CriterioRiesgoAbandono criterio,
                                     CriterioCambioComportamiento cambios, RiesgoAbandonoClienteRepository riesgos,
                                     Clock reloj) {
        this.agregado = agregado;
        this.criterio = criterio;
        this.cambios = cambios;
        this.riesgos = riesgos;
        this.reloj = reloj;
    }

    /**
     * Recalcula el riesgo de todos los clientes con compras vigentes y quita a quienes ya no tienen.
     * Una disminución cuenta si se detectó dentro del periodo actual de SCRUM-34.
     */
    @Transactional
    public ResultadoRecalculo recalcularTodos() {
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        Set<Long> conDisminucion = agregado.conDisminucionDesde(cambios.ventanas().inicioActual());
        Set<Long> evaluados = new HashSet<>();
        int enRiesgoAlto = 0;
        int nuevosEnRiesgoAlto = 0;
        for (ComprasCliente compras : agregado.porCliente()) {
            EvaluacionRiesgo evaluacion = criterio.evaluar(datos(compras, conDisminucion.contains(compras.clienteId())),
                    ahora);
            if (guardar(compras.clienteId(), evaluacion, ahora)) {
                nuevosEnRiesgoAlto++;
            }
            if (evaluacion.nivel() == NivelRiesgo.ALTO) {
                enRiesgoAlto++;
            }
            evaluados.add(compras.clienteId());
        }
        for (RiesgoAbandonoCliente registrado : riesgos.findAll()) {
            if (!evaluados.contains(registrado.getClienteId())) {
                riesgos.delete(registrado);
            }
        }
        log.info("Riesgo de abandono recalculado: {} clientes, {} en riesgo alto ({} nuevos)", evaluados.size(),
                enRiesgoAlto, nuevosEnRiesgoAlto);
        return new ResultadoRecalculo(evaluados.size(), enRiesgoAlto, nuevosEnRiesgoAlto);
    }

    /**
     * Recalcula a un solo cliente con lo que tiene vigente ahora; sin compras vigentes se quita su
     * riesgo. Corre en su propia transacción porque se llama después de confirmar la de la compra.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<EvaluacionRiesgo> recalcular(long clienteId) {
        Optional<ComprasCliente> compras = agregado.deCliente(clienteId);
        if (compras.isEmpty()) {
            riesgos.findById(clienteId).ifPresent(riesgos::delete);
            return Optional.empty();
        }
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        boolean disminucion = agregado.conDisminucionDesde(cambios.ventanas().inicioActual()).contains(clienteId);
        EvaluacionRiesgo evaluacion = criterio.evaluar(datos(compras.get(), disminucion), ahora);
        guardar(clienteId, evaluacion, ahora);
        return Optional.of(evaluacion);
    }

    /** Devuelve {@code true} si el cliente entra en riesgo ALTO con esta evaluación. */
    private boolean guardar(long clienteId, EvaluacionRiesgo evaluacion, OffsetDateTime ahora) {
        boolean entraEnAlto = riesgos.findById(clienteId)
                .map(existente -> existente.aplicar(evaluacion, ahora))
                .orElseGet(() -> {
                    riesgos.save(new RiesgoAbandonoCliente(clienteId, evaluacion, ahora));
                    return evaluacion.nivel() == NivelRiesgo.ALTO;
                });
        if (entraEnAlto) {
            log.info("Cliente {} en riesgo alto de abandono (puntaje {}, {} días sin comprar): queda para "
                    + "seguimiento comercial", clienteId, evaluacion.puntaje().toPlainString(), evaluacion.diasSinComprar());
        }
        return entraEnAlto;
    }

    private static DatosRiesgo datos(ComprasCliente compras, boolean disminucion) {
        return new DatosRiesgo(compras.compras(), compras.valorAcumulado(), compras.ultimaCompra(), disminucion);
    }

    /** Clientes evaluados, cuántos quedaron en riesgo alto y cuántos entraron en él en esta ejecución. */
    public record ResultadoRecalculo(int evaluados, int enRiesgoAlto, int nuevosEnRiesgoAlto) {
    }
}
