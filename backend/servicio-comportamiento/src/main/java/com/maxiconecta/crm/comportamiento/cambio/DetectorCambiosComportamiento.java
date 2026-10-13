package com.maxiconecta.crm.comportamiento.cambio;

import com.maxiconecta.crm.comportamiento.compra.AgregadoComprasCliente;
import com.maxiconecta.crm.comportamiento.compra.ComprasPorPeriodoCliente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Evalúa a todos los clientes con compras vigentes contra su línea base (SCRUM-34,
 * docs/compra/cambios-comportamiento.md) y registra cada variación relevante como una detección
 * auditable (SCRUM-319) que es, a la vez, la alerta de la ficha del cliente (SCRUM-315).
 * <p>
 * Una variación del mismo cliente, tipo y dirección ya detectada dentro del periodo actual no se
 * vuelve a registrar (criterio de aceptación 4): como la evaluación corre a diario con una ventana
 * móvil, una misma caída produce una alerta por periodo y no una por día.
 */
@Service
public class DetectorCambiosComportamiento {

    private static final Logger log = LoggerFactory.getLogger(DetectorCambiosComportamiento.class);

    private final AgregadoComprasCliente agregado;
    private final CriterioCambioComportamiento criterio;
    private final DeteccionCambioComportamientoRepository detecciones;
    private final EstadoComportamientoClienteRepository estados;

    public DetectorCambiosComportamiento(AgregadoComprasCliente agregado, CriterioCambioComportamiento criterio,
                                         DeteccionCambioComportamientoRepository detecciones,
                                         EstadoComportamientoClienteRepository estados) {
        this.agregado = agregado;
        this.criterio = criterio;
        this.detecciones = detecciones;
        this.estados = estados;
    }

    /**
     * Evalúa a todos los clientes, actualiza su estado, registra las detecciones nuevas y quita el
     * estado de quienes ya no tienen compras vigentes (las detecciones se conservan).
     */
    @Transactional
    public ResultadoDeteccion detectarTodos() {
        Ventanas ventanas = criterio.ventanas();
        Set<String> evaluados = new HashSet<>();
        int sinLineaBase = 0;
        int nuevas = 0;
        for (ComprasPorPeriodoCliente compras : agregado.comprasPorPeriodo(ventanas.inicioBase(),
                ventanas.inicioActual(), ventanas.fin())) {
            Evaluacion evaluacion = criterio.evaluar(compras, ventanas);
            guardarEstado(compras.idClienteOrigen(), evaluacion, ventanas);
            evaluados.add(compras.idClienteOrigen());
            if (evaluacion.estado() == EstadoComportamiento.SIN_LINEA_BASE) {
                sinLineaBase++;
            }
            for (Variacion variacion : evaluacion.variaciones()) {
                if (registrar(compras.idClienteOrigen(), variacion, ventanas)) {
                    nuevas++;
                }
            }
        }
        for (EstadoComportamientoCliente registrado : estados.findAll()) {
            if (!evaluados.contains(registrado.getIdClienteOrigen())) {
                estados.delete(registrado);
            }
        }
        return new ResultadoDeteccion(evaluados.size(), sinLineaBase, nuevas);
    }

    private void guardarEstado(String idClienteOrigen, Evaluacion evaluacion, Ventanas ventanas) {
        estados.findById(idClienteOrigen).ifPresentOrElse(
                existente -> existente.actualizar(evaluacion.estado(), evaluacion.comprasLineaBase(), ventanas.fin()),
                () -> estados.save(new EstadoComportamientoCliente(idClienteOrigen, evaluacion.estado(),
                        evaluacion.comprasLineaBase(), ventanas.fin())));
    }

    private boolean registrar(String idClienteOrigen, Variacion variacion, Ventanas ventanas) {
        if (detecciones.existsByIdClienteOrigenAndTipoAndDireccionAndDetectadoEnAfter(idClienteOrigen,
                variacion.tipo(), variacion.direccion(), ventanas.inicioActual())) {
            return false;
        }
        detecciones.save(new DeteccionCambioComportamiento(idClienteOrigen, variacion, criterio.umbral(), ventanas));
        log.info("Cambio de comportamiento detectado: cliente={} tipo={} direccion={} actual={} habitual={} "
                        + "variacion={} periodo=[{}, {})", idClienteOrigen, variacion.tipo(), variacion.direccion(),
                variacion.actual().toPlainString(), variacion.habitual().toPlainString(),
                variacion.relativa().toPlainString(), ventanas.inicioActual(), ventanas.fin());
        return true;
    }

    /** Cuántos clientes se evaluaron, cuántos quedaron sin línea base y cuántas detecciones nuevas hubo. */
    public record ResultadoDeteccion(int evaluados, int sinLineaBase, int deteccionesNuevas) {
    }
}
