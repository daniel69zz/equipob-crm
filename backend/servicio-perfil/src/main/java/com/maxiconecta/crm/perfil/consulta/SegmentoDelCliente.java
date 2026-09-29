package com.maxiconecta.crm.perfil.consulta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Segmento asignado al cliente, consultado en servicio-segmentacion para la ficha integral
 * (SCRUM-149). Si el servicio no responde, se trata igual que "sin segmento asignado" en vez de
 * hacer fallar la ficha (CA2 de SCRUM-10).
 */
@Component
public class SegmentoDelCliente {

    private static final Logger log = LoggerFactory.getLogger(SegmentoDelCliente.class);

    private final RestClient http;

    public SegmentoDelCliente(@Qualifier("segmentacion") RestClient http) {
        this.http = http;
    }

    public SegmentoResponse buscar(Long clienteId) {
        try {
            SegmentoResponse respuesta = http.get()
                    .uri("/api/segmentacion/clientes/{clienteId}/segmento", clienteId)
                    .retrieve().body(SegmentoResponse.class);
            return respuesta != null ? respuesta : SegmentoResponse.SIN_DATOS;
        } catch (RestClientException ex) {
            log.warn("No se pudo consultar el segmento del cliente {}; la ficha integral seguirá "
                    + "cargando sin ese bloque ({})", clienteId, ex.getClass().getSimpleName());
            return SegmentoResponse.SIN_DATOS;
        }
    }
}
