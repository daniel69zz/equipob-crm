package com.maxiconecta.crm.comportamiento.ingesta;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Bitácora de ingesta: eventos recibidos, procesados, fallidos y descartados por periodo.
 * El API Gateway exige el permiso EVENTOS_REPROCESAR para /api/comportamiento/eventos/**.
 */
@RestController
@RequestMapping("/api/comportamiento/eventos")
public class BitacoraController {

    private final ConsultaBitacora consulta;

    public BitacoraController(ConsultaBitacora consulta) {
        this.consulta = consulta;
    }

    @GetMapping
    public Bitacora buscar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                           @RequestParam(required = false) EstadoEvento estado,
                           @RequestParam(required = false) String origen,
                           @RequestParam(defaultValue = "0") int pagina,
                           @RequestParam(defaultValue = "50") int tamanio) {
        FiltroBitacora filtro = new FiltroBitacora(desde, hasta, estado, origen);
        Page<EventoRecibido> resultado = consulta.buscar(filtro, pagina, tamanio);
        return new Bitacora(filtro.desde(), filtro.hasta(), consulta.resumen(filtro),
                resultado.getContent().stream().map(EventoResponse::de).toList(),
                resultado.getNumber(), resultado.getSize(), resultado.getTotalElements());
    }

    public record Bitacora(LocalDate desde, LocalDate hasta, Map<EstadoEvento, Long> resumen,
                           List<EventoResponse> eventos, int pagina, int tamanio, long total) {
    }

    public record EventoResponse(Long id, String idEventoOrigen, String tipoEvento, String origen, EstadoEvento estado,
                                 String causa, OffsetDateTime recibidoEn, OffsetDateTime procesadoEn) {

        static EventoResponse de(EventoRecibido evento) {
            return new EventoResponse(evento.getId(), evento.getIdEventoOrigen(), evento.getTipoEvento(),
                    evento.getOrigen(), evento.getEstado(), evento.getCausa(), evento.getRecibidoEn(),
                    evento.getProcesadoEn());
        }
    }
}
