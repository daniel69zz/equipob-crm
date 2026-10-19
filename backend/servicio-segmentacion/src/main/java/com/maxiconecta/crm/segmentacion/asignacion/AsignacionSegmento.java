package com.maxiconecta.crm.segmentacion.asignacion;

import com.maxiconecta.crm.segmentacion.asignacion.AuditoriaSegmentos.Operacion;
import com.maxiconecta.crm.segmentacion.asignacion.RegistroSegmentos.SegmentoActual;
import com.maxiconecta.crm.segmentacion.criterio.Criterio;
import com.maxiconecta.crm.segmentacion.criterio.CriterioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Asigna el segmento de un cliente a partir de sus métricas vigentes (SCRUM-261, SCRUM-536;
 * docs/segmentacion/asignacion-automatica.md). Todo ocurre en una transacción con el cliente
 * bloqueado: el evento aplicado, la foto de métricas, el segmento y su auditoría se guardan juntos
 * o no se guarda nada.
 */
@Service
public class AsignacionSegmento {

    private static final Logger log = LoggerFactory.getLogger(AsignacionSegmento.class);

    private final RegistroSegmentos registro;
    private final CriterioRepository criterios;
    private final AuditoriaSegmentos auditoria;

    public AsignacionSegmento(RegistroSegmentos registro, CriterioRepository criterios, AuditoriaSegmentos auditoria) {
        this.registro = registro;
        this.criterios = criterios;
        this.auditoria = auditoria;
    }

    @Transactional
    public Resultado aplicar(MetricasCliente metricas) {
        long clienteId = metricas.idCliente();
        registro.bloquearCliente(clienteId);

        // SCRUM-241: una reentrega del mismo evento no recalcula el segmento.
        if (registro.eventoYaProcesado(metricas.idEvento())) {
            log.info("Evento {} ya aplicado al cliente {}: se descarta", metricas.idEvento(), clienteId);
            return Resultado.DUPLICADO;
        }
        // Un mensaje calculado antes que la foto vigente (reintento atrasado) no la pisa.
        Optional<OffsetDateTime> vigentes = registro.metricasAplicadasEn(clienteId);
        if (vigentes.isPresent() && metricas.calculadasEn().isBefore(vigentes.get())) {
            registro.registrarEvento(metricas.idEvento(), clienteId, Resultado.DESACTUALIZADO.name());
            log.info("Evento {} del cliente {} calculado antes que la foto vigente: no cambia el segmento",
                    metricas.idEvento(), clienteId);
            return Resultado.DESACTUALIZADO;
        }

        registro.guardarMetricas(metricas);
        Optional<Criterio> elegido = ReglaPrioridadSegmento.elegir(criterios.activos(), metricas.frecuencia(),
                metricas.valor(), metricas.categoriasOVacio(), metricas.clasificacionFrecuencia());
        SegmentoActual anterior = registro.segmento(clienteId).orElse(null);
        actualizar(clienteId, anterior, elegido.orElse(null), metricas);
        registro.registrarEvento(metricas.idEvento(), clienteId, Resultado.APLICADO.name());
        return Resultado.APLICADO;
    }

    private void actualizar(long clienteId, SegmentoActual anterior, Criterio nuevo, MetricasCliente metricas) {
        if (nuevo == null) {
            if (anterior != null) {
                registro.retirar(clienteId);
                auditoria.registrar(Operacion.SEGMENTO_RETIRADO, clienteId, anterior, null, metricas);
            }
            return;
        }
        if (anterior == null) {
            registro.asignar(clienteId, nuevo, metricas.idEvento());
            auditoria.registrar(Operacion.SEGMENTO_ASIGNADO, clienteId, null, nuevo, metricas);
        } else if (!Objects.equals(anterior.criterioId(), nuevo.id()) || !anterior.segmento().equals(nuevo.nombre())) {
            registro.asignar(clienteId, nuevo, metricas.idEvento());
            auditoria.registrar(Operacion.SEGMENTO_CAMBIADO, clienteId, anterior, nuevo, metricas);
        }
    }

    public enum Resultado { APLICADO, DESACTUALIZADO, DUPLICADO }
}
