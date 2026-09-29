package com.maxiconecta.crm.perfil.consulta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.maxiconecta.crm.perfil.cliente.ClienteOrigen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Historial de compras del cliente, consultado en servicio-comportamiento con sus identificadores
 * de origen (SCRUM-151, docs/compra/historial-compras.md). Si el servicio no responde, la ficha
 * integral no debe fallar: se degrada a una lista vacía (mismo criterio que {@code ClientePerfiles}
 * en servicio-comportamiento).
 */
@Component
public class HistorialComprasCliente {

    private static final Logger log = LoggerFactory.getLogger(HistorialComprasCliente.class);

    private final RestClient http;

    public HistorialComprasCliente(@Qualifier("comportamiento") RestClient http) {
        this.http = http;
    }

    public List<CompraResponse> buscar(Long clienteId, List<ClienteOrigen> identificadoresOrigen) {
        if (identificadoresOrigen.isEmpty()) {
            return List.of();
        }
        try {
            HistorialCompras historial = http.get()
                    .uri(builder -> {
                        builder.path("/api/comportamiento/clientes/{clienteId}/compras")
                                .queryParam("tamanio", 100);
                        identificadoresOrigen.forEach(vinculo -> builder.queryParam("identificador", vinculo.getIdClienteOrigen()));
                        return builder.build(clienteId);
                    })
                    .retrieve().body(HistorialCompras.class);
            return historial != null && historial.content() != null ? historial.content() : List.of();
        } catch (RestClientException ex) {
            log.warn("No se pudo consultar el historial de compras del cliente {}; la ficha integral seguirá "
                    + "cargando sin ese bloque ({})", clienteId, ex.getClass().getSimpleName());
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record HistorialCompras(List<CompraResponse> content) {
    }
}
