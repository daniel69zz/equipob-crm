package com.maxiconecta.crm.comportamiento.ingesta;

import com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Filtros de la bitácora. Cada fecha es opcional; sin fechas se consulta todo el historial.
 * Las fechas son días completos en la zona horaria del servidor.
 */
public record FiltroBitacora(LocalDate desde, LocalDate hasta, EstadoEvento estado,
                             String transaccion) {

    public FiltroBitacora {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ReglaNegocioException("La fecha 'desde' no puede ser posterior a 'hasta'");
        }
        transaccion = transaccion == null || transaccion.isBlank() ? null : transaccion.trim();
    }

    FiltroBitacora conEstado(EstadoEvento otroEstado) {
        return new FiltroBitacora(desde, hasta, otroEstado, transaccion);
    }

    Specification<EventoRecibido> comoEspecificacion(ZoneId zona) {
        return (evento, consulta, criterios) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (desde != null) {
                condiciones.add(criterios.greaterThanOrEqualTo(evento.get("recibidoEn"),
                        desde.atStartOfDay(zona).toOffsetDateTime()));
            }
            if (hasta != null) {
                condiciones.add(criterios.lessThan(evento.get("recibidoEn"),
                        hasta.plusDays(1).atStartOfDay(zona).toOffsetDateTime()));
            }
            if (estado != null) {
                condiciones.add(criterios.equal(evento.get("estado"),
                        estado == EstadoEvento.EN_ESPERA ? EstadoEvento.FALLIDO : estado));
                if (estado == EstadoEvento.EN_ESPERA || estado == EstadoEvento.FALLIDO) {
                    var pendientes = consulta.subquery(Integer.class);
                    var intento = pendientes.from(IntentoReproceso.class);
                    pendientes.select(criterios.literal(1)).where(
                            criterios.equal(intento.get("evento"), evento),
                            criterios.equal(intento.get("resultado"), ResultadoReproceso.EN_PROCESO));
                    var enEspera = criterios.exists(pendientes);
                    condiciones.add(estado == EstadoEvento.EN_ESPERA ? enEspera : criterios.not(enEspera));
                }
            }
            if (transaccion != null) {
                condiciones.add(criterios.equal(evento.get("idTransaccion"), transaccion));
            }
            return criterios.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
