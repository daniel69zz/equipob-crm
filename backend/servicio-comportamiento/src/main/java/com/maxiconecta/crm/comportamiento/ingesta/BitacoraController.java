package com.maxiconecta.crm.comportamiento.ingesta;

import com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

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
    private final ReprocesadorEventos reprocesador;
    private final ReinyectorMensajesRespaldo reinyector;

    public BitacoraController(ConsultaBitacora consulta, ReprocesadorEventos reprocesador,
                              ReinyectorMensajesRespaldo reinyector) {
        this.consulta = consulta;
        this.reprocesador = reprocesador;
        this.reinyector = reinyector;
    }

    @GetMapping
    public Bitacora buscar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                           @RequestParam(required = false) EstadoEvento estado,
                           @RequestParam(required = false) String transaccion,
                           @RequestParam(defaultValue = "0") int pagina,
                           @RequestParam(defaultValue = "50") int tamanio) {
        FiltroBitacora filtro = new FiltroBitacora(desde, hasta, estado, transaccion);
        Page<EventoRecibido> resultado = consulta.buscar(filtro, pagina, tamanio);
        return new Bitacora(filtro.desde(), filtro.hasta(), consulta.resumen(filtro),
                resultado.getContent().stream().map(EventoResponse::de).toList(),
                resultado.getNumber(), resultado.getSize(), resultado.getTotalElements());
    }

    public record Bitacora(LocalDate desde, LocalDate hasta, Map<EstadoEvento, Long> resumen,
                           List<EventoResponse> eventos, int pagina, int tamanio, long total) {
    }

    public record EventoResponse(Long id, String idEventoOrigen, String tipoEvento,
                                 String idTransaccion, EstadoEvento estado, String causa, OffsetDateTime recibidoEn,
                                 OffsetDateTime procesadoEn) {

        static EventoResponse de(EventoRecibido evento) {
            return new EventoResponse(evento.getId(), evento.getIdEventoOrigen(), evento.getTipoEvento(),
                    evento.getIdTransaccion(), evento.getEstado(), evento.getCausa(),
                    evento.getRecibidoEn(), evento.getProcesadoEn());
        }
    }

    @PostMapping("/{id}/reprocesar")
    public IntentoResponse reprocesar(@PathVariable Long id,
                                      @RequestHeader(name = "X-Usuario", required = false) String usuario) {
        return IntentoResponse.de(reprocesador.reprocesar(id, usuario));
    }

    @GetMapping("/{id}/intentos")
    public List<IntentoResponse> intentos(@PathVariable Long id) {
        return reprocesador.intentos(id).stream().map(IntentoResponse::de).toList();
    }

    /**
     * Reinyecta un mensaje de la cola de respaldo indicada: {@code compras} (por defecto) o
     * {@code anulaciones}.
     */
    @PostMapping("/respaldo/reinyectar")
    public ResponseEntity<ReinyeccionResponse> reinyectarRespaldo(
            @RequestParam(defaultValue = "compras") String cola,
            @RequestHeader(name = "X-Usuario", required = false) String usuario) {
        return reinyector.reinyectarUno(colaRespaldo(cola), usuario)
                .map(resultado -> ResponseEntity.ok(new ReinyeccionResponse(true, resultado.idMensaje(),
                        resultado.bytes())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private static ReinyectorMensajesRespaldo.ColaRespaldo colaRespaldo(String cola) {
        try {
            return ReinyectorMensajesRespaldo.ColaRespaldo.valueOf(cola.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Cola de respaldo desconocida: " + cola + " (se espera compras o anulaciones)");
        }
    }

    public record IntentoResponse(Long id, Long idEvento, int numero, OffsetDateTime intentadoEn,
                                  ResultadoReproceso resultado, String causa, String usuario) {

        static IntentoResponse de(IntentoReproceso intento) {
            return new IntentoResponse(intento.getId(), intento.getIdEvento(), intento.getNumero(),
                    intento.getIntentadoEn(), intento.getResultado(), intento.getCausa(), intento.getUsuario());
        }
    }

    public record ReinyeccionResponse(boolean reinyectado, String idMensaje, int bytes) {
    }
}
