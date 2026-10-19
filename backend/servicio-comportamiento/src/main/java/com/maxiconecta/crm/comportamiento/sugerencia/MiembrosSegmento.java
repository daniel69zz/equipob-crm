package com.maxiconecta.crm.comportamiento.sugerencia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.maxiconecta.crm.comportamiento.configuracion.ConfiguracionSegmentacion;
import com.maxiconecta.crm.comportamiento.preferencia.PreferenciasException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Objects;

/**
 * SCRUM-378: pide a servicio-segmentacion el segmento vigente del cliente y sus compañeros de
 * segmento. Una respuesta incompleta no se confunde con «sin segmento»: es un 503.
 */
@Component
public class MiembrosSegmento {
    private final RestClient http;

    public MiembrosSegmento(@Qualifier(ConfiguracionSegmentacion.CLIENTE) RestClient http) { this.http = http; }

    public Segmento obtener(long clienteId) {
        Respuesta respuesta;
        try {
            respuesta = http.get().uri("/api/segmentacion/clientes/{id}/segmento/miembros", clienteId)
                    .retrieve().body(Respuesta.class);
        } catch (RestClientException ex) {
            throw noDisponible();
        }
        if (respuesta == null || respuesta.clientes() == null) { throw noDisponible(); }
        if (respuesta.sinDatos()) { return Segmento.SIN_SEGMENTO; }
        if (respuesta.segmento() == null || respuesta.segmento().isBlank()
                || respuesta.clientes().stream().anyMatch(id -> id == null || id <= 0)) {
            throw noDisponible();
        }
        List<Long> companeros = respuesta.clientes().stream()
                .filter(id -> !Objects.equals(id, clienteId)).distinct().toList();
        return new Segmento(respuesta.segmento(), companeros);
    }

    private static PreferenciasException noDisponible() {
        return new PreferenciasException(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo consultar el segmento del cliente en el servicio de segmentación; intente nuevamente");
    }

    /** {@code nombre} nulo: el cliente no tiene segmento asignado. */
    public record Segmento(String nombre, List<Long> companeros) {
        static final Segmento SIN_SEGMENTO = new Segmento(null, List.of());

        public Segmento { companeros = List.copyOf(companeros); }

        public boolean asignado() { return nombre != null; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Respuesta(String segmento, boolean sinDatos, List<Long> clientes) { }
}
