package com.maxiconecta.crm.interacciones.perfil;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Consulta el perfil del cliente en servicio-perfil ({@code GET /api/perfil/clientes/{id}}): comprueba que exista
 * antes de guardar una interacción (SCRUM-432) y da sus identificadores en Marketplace y Ventas para buscar sus
 * compras (SCRUM-53).
 */
@Component
public class ClientesPerfil {

    private final RestClient http;

    public ClientesPerfil(@Qualifier("perfil") RestClient http) {
        this.http = http;
    }

    /**
     * @return si el cliente existe
     * @throws ResponseStatusException 503 si el servicio de Perfil no responde: la interacción no se guarda
     */
    public boolean existe(long clienteId) {
        return identificadores(clienteId).isPresent();
    }

    /**
     * @return los identificadores del cliente en Marketplace y Ventas (vacía si no tiene), o vacío si el cliente no
     *         existe
     * @throws ResponseStatusException 503 si el servicio de Perfil no responde
     */
    public Optional<List<String>> identificadores(long clienteId) {
        try {
            Perfil perfil = http.get().uri("/api/perfil/clientes/{id}", clienteId).retrieve().body(Perfil.class);
            List<Identificador> vinculos = perfil == null || perfil.identificadoresOrigen() == null
                    ? List.of() : perfil.identificadoresOrigen();
            return Optional.of(vinculos.stream().map(Identificador::idCliente).filter(Objects::nonNull)
                    .filter(id -> !id.isBlank()).toList());
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo consultar el cliente en el servicio de Perfil; intente de nuevo en unos minutos");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Perfil(List<Identificador> identificadoresOrigen) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Identificador(String idCliente) {
    }
}
