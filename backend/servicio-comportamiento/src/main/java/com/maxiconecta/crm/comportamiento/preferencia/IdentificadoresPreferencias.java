package com.maxiconecta.crm.comportamiento.preferencia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.maxiconecta.crm.comportamiento.compra.Identificador;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

/** SCRUM-365: resuelve en el servidor todos los identificadores del cliente CRM indicado. */
@Component
public class IdentificadoresPreferencias {
    private final RestClient http;
    public IdentificadoresPreferencias(RestClient http) { this.http = http; }

    public List<Identificador> obtener(long clienteId) {
        if (clienteId <= 0) {
            throw new PreferenciasException(HttpStatus.BAD_REQUEST, "El identificador del cliente debe ser positivo");
        }
        Perfil perfil;
        try {
            perfil = http.get().uri("/api/perfil/clientes/{id}", clienteId).retrieve().body(Perfil.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new PreferenciasException(HttpStatus.NOT_FOUND, "No existe el cliente " + clienteId);
        } catch (RestClientException ex) {
            throw noDisponible();
        }
        if (perfil == null || perfil.id() == null || perfil.id() != clienteId
                || perfil.identificadoresOrigen() == null) {
            throw noDisponible();
        }
        if (perfil.idClienteConsolidado() != null) {
            throw new PreferenciasException(HttpStatus.CONFLICT,
                    "El cliente fue consolidado; utilice el cliente " + perfil.idClienteConsolidado());
        }
        if (perfil.identificadoresOrigen().stream().anyMatch(i -> i == null || i.idCliente() == null
                || i.idCliente().isBlank() || i.idCliente().strip().length() > 64)) {
            throw noDisponible();
        }
        return perfil.identificadoresOrigen().stream().map(i -> Identificador.parsear(i.idCliente())).distinct().toList();
    }

    private static PreferenciasException noDisponible() {
        return new PreferenciasException(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo verificar el cliente en el servicio de perfiles; intente nuevamente");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Perfil(Long id, Long idClienteConsolidado, List<Origen> identificadoresOrigen) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Origen(String idCliente) { }
}
