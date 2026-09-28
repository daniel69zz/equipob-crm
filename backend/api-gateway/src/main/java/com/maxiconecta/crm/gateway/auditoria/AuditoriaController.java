package com.maxiconecta.crm.gateway.auditoria;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Consulta de auditoría. Solo lectura: no hay rutas para modificar ni borrar registros.
 */
@RestController
@RequestMapping("/api/admin/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    /**
     * Eventos de auditoría filtrados por usuario, operación, cliente y rango de fechas, paginados.
     */
    @GetMapping
    public PaginaEventos buscar(@RequestParam(required = false) String usuario,
                                @RequestParam(required = false) String operacion,
                                @RequestParam(required = false) String clienteId,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                @RequestParam(defaultValue = "0") int pagina,
                                @RequestParam(defaultValue = "50") int tamanio) {
        FiltroAuditoria filtro = new FiltroAuditoria(usuario, operacion, clienteId, desde, hasta);
        return PaginaEventos.de(auditoriaService.buscar(filtro, pagina, tamanio));
    }

    @GetMapping("/operaciones")
    public List<String> operaciones() {
        return AuditoriaService.OPERACIONES;
    }

    public record PaginaEventos(List<EventoResponse> eventos, int pagina, int tamanio, long total) {

        static PaginaEventos de(Page<EventoAuditoria> pagina) {
            return new PaginaEventos(pagina.getContent().stream().map(EventoResponse::de).toList(),
                    pagina.getNumber(), pagina.getSize(), pagina.getTotalElements());
        }
    }

    public record EventoResponse(Long id, OffsetDateTime ocurridoEn, String usuario, String operacion,
                                 String entidad, String entidadId, String detalle) {

        static EventoResponse de(EventoAuditoria evento) {
            return new EventoResponse(evento.getId(), evento.getOcurridoEn(), evento.getUsuario(),
                    evento.getOperacion(), evento.getEntidad(), evento.getEntidadId(), evento.getDetalle());
        }
    }
}
