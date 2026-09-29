package com.maxiconecta.crm.perfil.consulta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Saldo de puntos del cliente, consultado en servicio-fidelizacion para la ficha integral
 * (SCRUM-154). Si el servicio no responde, se trata igual que "sin puntos asignados" en vez de
 * hacer fallar la ficha (CA2 de SCRUM-10).
 */
@Component
public class PuntosDelCliente {

    private static final Logger log = LoggerFactory.getLogger(PuntosDelCliente.class);

    private final RestClient http;

    public PuntosDelCliente(@Qualifier("fidelizacion") RestClient http) {
        this.http = http;
    }

    public PuntosResponse buscar(Long clienteId) {
        try {
            PuntosResponse respuesta = http.get()
                    .uri("/api/fidelizacion/clientes/{clienteId}/puntos", clienteId)
                    .retrieve().body(PuntosResponse.class);
            return respuesta != null ? respuesta : PuntosResponse.SIN_DATOS;
        } catch (RestClientException ex) {
            log.warn("No se pudo consultar los puntos del cliente {}; la ficha integral seguirá "
                    + "cargando sin ese bloque ({})", clienteId, ex.getClass().getSimpleName());
            return PuntosResponse.SIN_DATOS;
        }
    }
}
