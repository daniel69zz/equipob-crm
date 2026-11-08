package com.maxiconecta.crm.interacciones.perfil;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Comprueba que el cliente de una interacción exista en el CRM (SCRUM-432) con
 * {@code GET /api/perfil/clientes/{id}} de servicio-perfil.
 */
@Component
public class ClientesPerfil {

    private final RestClient http;

    public ClientesPerfil(RestClient http) {
        this.http = http;
    }

    /**
     * @return si el cliente existe
     * @throws ResponseStatusException 503 si el servicio de Perfil no responde: la interacción no se guarda
     */
    public boolean existe(long clienteId) {
        try {
            http.get().uri("/api/perfil/clientes/{id}", clienteId).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound ex) {
            return false;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo comprobar el cliente en el servicio de Perfil; intente de nuevo en unos minutos");
        }
    }
}
