package com.maxiconecta.crm.gateway.auditoria;

import com.maxiconecta.crm.gateway.comun.ReglaNegocioException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Filtros de la consulta de auditoría. Todos son opcionales; las fechas son días completos
 * en la zona horaria del servidor.
 */
public record FiltroAuditoria(String usuario, String operacion, String clienteId, LocalDate desde, LocalDate hasta) {

    public FiltroAuditoria {
        usuario = limpiar(usuario);
        operacion = limpiar(operacion);
        clienteId = limpiar(clienteId);
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ReglaNegocioException("La fecha 'desde' no puede ser posterior a 'hasta'");
        }
    }

    Specification<EventoAuditoria> comoEspecificacion(ZoneId zona) {
        return (evento, consulta, criterios) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (usuario != null) {
                condiciones.add(criterios.equal(criterios.lower(evento.get("usuario")), usuario.toLowerCase()));
            }
            if (operacion != null) {
                condiciones.add(criterios.equal(evento.get("operacion"), operacion));
            }
            if (clienteId != null) {
                condiciones.add(criterios.equal(evento.get("entidad"), AuditoriaService.ENTIDAD_CLIENTE));
                condiciones.add(criterios.equal(evento.get("entidadId"), clienteId));
            }
            if (desde != null) {
                condiciones.add(criterios.greaterThanOrEqualTo(evento.get("ocurridoEn"),
                        desde.atStartOfDay(zona).toOffsetDateTime()));
            }
            if (hasta != null) {
                condiciones.add(criterios.lessThan(evento.get("ocurridoEn"),
                        hasta.plusDays(1).atStartOfDay(zona).toOffsetDateTime()));
            }
            return criterios.and(condiciones.toArray(Predicate[]::new));
        };
    }

    private static String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
