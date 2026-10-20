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
import java.util.Set;

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
    private final RegistroIntentosReproceso registro;

    public BitacoraController(ConsultaBitacora consulta, ReprocesadorEventos reprocesador,
                              ReinyectorMensajesRespaldo reinyector, RegistroIntentosReproceso registro) {
        this.consulta = consulta;
        this.reprocesador = reprocesador;
        this.reinyector = reinyector;
        this.registro = registro;
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
        Set<Long> reprocesados = consulta.reprocesados(resultado.getContent());
        Set<Long> enEspera = consulta.enEspera(resultado.getContent());
        return new Bitacora(filtro.desde(), filtro.hasta(), consulta.resumen(filtro),
                resultado.getContent().stream().map(evento -> EventoResponse.de(evento,
                        reprocesados.contains(evento.getId()), enEspera.contains(evento.getId()))).toList(),
                resultado.getNumber(), resultado.getSize(), resultado.getTotalElements());
    }

    public record Bitacora(LocalDate desde, LocalDate hasta, Map<EstadoEvento, Long> resumen,
                           List<EventoResponse> eventos, int pagina, int tamanio, long total) {
    }

    public record EventoResponse(Long id, String idEventoOrigen, String tipoEvento,
                                 String idTransaccion, EstadoEvento estado, String causa, OffsetDateTime recibidoEn,
                                 OffsetDateTime procesadoEn, boolean reprocesado, boolean enRevision) {

        static EventoResponse de(EventoRecibido evento, boolean reprocesado, boolean enEspera) {
            return new EventoResponse(evento.getId(), evento.getIdEventoOrigen(), evento.getTipoEvento(),
                    evento.getIdTransaccion(), enEspera ? EstadoEvento.EN_ESPERA : evento.getEstado(), evento.getCausa(),
                    evento.getRecibidoEn(), evento.getProcesadoEn(), reprocesado || enEspera, enEspera);
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

    @PostMapping("/{id}/revision")
    public IntentoResponse enviarARevision(@PathVariable Long id,
                                           @RequestHeader(name = "X-Usuario", required = false) String usuario) {
        return IntentoResponse.de(registro.enviarARevision(id, usuario));
    }

    /**
     * Reinyecta un mensaje de la cola de respaldo indicada: {@code compras} (por defecto),
     * {@code anulaciones} o {@code preferencias} (SCRUM-42).
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
            throw new ReglaNegocioException("Cola de respaldo desconocida: " + cola + " (se espera compras, anulaciones o preferencias)");
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
