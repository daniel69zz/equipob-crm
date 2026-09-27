package com.maxiconecta.crm.gateway.auditoria;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    /**
     * Últimos 100 eventos de auditoría, opcionalmente filtrados por operación.
     */
    @GetMapping
    public List<EventoResponse> listar(@RequestParam(required = false) String operacion) {
        return auditoriaService.ultimos(operacion).stream().map(EventoResponse::de).toList();
    }

    public record EventoResponse(Long id, OffsetDateTime ocurridoEn, String usuario, String operacion,
                                 String entidad, String entidadId, String detalle) {

        static EventoResponse de(EventoAuditoria evento) {
            return new EventoResponse(evento.getId(), evento.getOcurridoEn(), evento.getUsuario(),
                    evento.getOperacion(), evento.getEntidad(), evento.getEntidadId(), evento.getDetalle());
        }
    }
}
