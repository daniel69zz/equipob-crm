package com.maxiconecta.crm.interacciones.compras;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Compras del cliente en servicio-comportamiento ({@code GET /api/comportamiento/clientes/{clienteId}/compras}),
 * buscadas por sus identificadores en Marketplace y Ventas, como hace la ficha integral (SCRUM-151). Si el servicio
 * no responde, el historial no falla (SCRUM-441).
 */
@Component
public class ComprasCliente {

    private static final Logger log = LoggerFactory.getLogger(ComprasCliente.class);

    private final RestClient http;

    public ComprasCliente(@Qualifier("comportamiento") RestClient http) {
        this.http = http;
    }

    /** Las {@code limite} compras más recientes; se llama solo con identificadores. */
    public ComprasRecientes recientes(long clienteId, List<String> identificadores, int limite) {
        try {
            Pagina pagina = http.get()
                    .uri(builder -> {
                        builder.path("/api/comportamiento/clientes/{clienteId}/compras")
                                .queryParam("pagina", 0).queryParam("tamanio", limite);
                        identificadores.forEach(id -> builder.queryParam("identificador", id));
                        return builder.build(clienteId);
                    })
                    .retrieve().body(Pagina.class);
            if (pagina == null || pagina.content() == null) {
                return ComprasRecientes.ninguna();
            }
            return new ComprasRecientes(pagina.content(), Math.max(pagina.total(), pagina.content().size()), true);
        } catch (RestClientException ex) {
            log.warn("No se pudieron consultar las compras del cliente {}; el historial seguirá sin ellas ({})",
                    clienteId, ex.getClass().getSimpleName());
            return ComprasRecientes.noDisponibles();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Pagina(List<Compra> content, long total) {
    }
}
