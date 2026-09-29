package com.maxiconecta.crm.comportamiento.ingesta;

import com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Periodo y filtros de la bitácora de ingesta. Sin fechas, el periodo son los últimos 7 días.
 * Las fechas son días completos en la zona horaria del servidor.
 */
public record FiltroBitacora(LocalDate desde, LocalDate hasta, EstadoEvento estado,
                             String transaccion) {

    static final int DIAS_POR_DEFECTO = 7;

    public FiltroBitacora {
        LocalDate hoy = LocalDate.now();
        if (hasta == null) {
            hasta = desde != null && desde.isAfter(hoy) ? desde : hoy;
        }
        if (desde == null) {
            desde = hasta.minusDays(DIAS_POR_DEFECTO - 1);
        }
        if (desde.isAfter(hasta)) {
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
            condiciones.add(criterios.greaterThanOrEqualTo(evento.get("recibidoEn"),
                    desde.atStartOfDay(zona).toOffsetDateTime()));
            condiciones.add(criterios.lessThan(evento.get("recibidoEn"),
                    hasta.plusDays(1).atStartOfDay(zona).toOffsetDateTime()));
            if (estado != null) {
                condiciones.add(criterios.equal(evento.get("estado"), estado));
            }
            if (transaccion != null) {
                condiciones.add(criterios.equal(evento.get("idTransaccion"), transaccion));
            }
            return criterios.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
