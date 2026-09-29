package com.maxiconecta.crm.comportamiento.compra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

/** Resuelve el identificador externo sin crear perfiles ni escribir en el esquema de Perfil. */
@Component
public class ClientePerfiles {

    private static final Logger log = LoggerFactory.getLogger(ClientePerfiles.class);
    private final RestClient http;

    public ClientePerfiles(RestClient http) {
        this.http = http;
    }

    public Optional<Long> buscar(Origen origen, String idClienteOrigen) {
        try {
            PaginaPerfiles pagina = http.get()
                    .uri(builder -> builder.path("/api/perfil/clientes")
                            .queryParam("origen", "{origen}")
                            .queryParam("idClienteOrigen", "{cliente}")
                            .build(origen.name(), idClienteOrigen))
                    .retrieve().body(PaginaPerfiles.class);
            if (pagina != null && pagina.total() == 1 && pagina.clientes() != null
                    && pagina.clientes().size() == 1) {
                Perfil perfil = pagina.clientes().get(0);
                if (perfil != null && perfil.id() != null && perfil.id() > 0) {
                    return Optional.of(perfil.id());
                }
            }
        } catch (RestClientException ex) {
            // La compra sigue siendo válida aunque el servicio de perfiles no esté disponible.
            log.warn("No se pudo consultar Perfil; la compra quedará pendiente de vinculación ({})",
                    ex.getClass().getSimpleName());
        }
        return Optional.empty();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaginaPerfiles(List<Perfil> clientes, long total) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Perfil(Long id) {
    }
}
