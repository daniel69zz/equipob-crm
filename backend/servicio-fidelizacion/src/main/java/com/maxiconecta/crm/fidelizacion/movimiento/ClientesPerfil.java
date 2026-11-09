package com.maxiconecta.crm.fidelizacion.movimiento;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

/**
 * Vincula el cliente de Marketplace y Ventas de un movimiento con su perfil del CRM, con la misma
 * consulta que usa servicio-comportamiento para las compras. No crea perfiles.
 */
@Component
public class ClientesPerfil {

    private final RestClient http;

    public ClientesPerfil(RestClient http) {
        this.http = http;
    }

    /**
     * @return el cliente del CRM, o vacío si no hay un único perfil con ese identificador de origen
     * @throws PerfilNoDisponibleException si el servicio de Perfil no responde
     */
    public Optional<Long> buscar(String idClienteOrigen) {
        PaginaPerfiles pagina;
        try {
            pagina = http.get()
                    .uri(builder -> builder.path("/api/perfil/clientes")
                            .queryParam("idClienteOrigen", "{cliente}")
                            .build(idClienteOrigen))
                    .retrieve().body(PaginaPerfiles.class);
        } catch (RestClientException ex) {
            throw new PerfilNoDisponibleException("No se pudo consultar el servicio de Perfil ("
                    + ex.getClass().getSimpleName() + "); el movimiento queda para reproceso");
        }
        if (pagina != null && pagina.total() == 1 && pagina.clientes() != null && pagina.clientes().size() == 1) {
            Perfil perfil = pagina.clientes().get(0);
            if (perfil != null && perfil.id() != null && perfil.id() > 0) {
                return Optional.of(perfil.id());
            }
        }
        return Optional.empty();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaginaPerfiles(List<Perfil> clientes, long total) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Perfil(Long id) {
    }

    /** Se consulta solo en el despachador: una caida de Perfil no bloquea un ajuste manual. */
    public String identificador(long clienteId) {
        PerfilIdentificadores perfil = http.get().uri("/api/perfil/clientes/{id}", clienteId)
                .retrieve().body(PerfilIdentificadores.class);
        List<String> ids = perfil == null || perfil.identificadoresOrigen() == null ? List.of()
                : perfil.identificadoresOrigen().stream().map(Identificador::idCliente)
                    .filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (ids.size() != 1) {
            throw new IllegalStateException("El cliente " + clienteId + " requiere un identificador unico de Marketplace");
        }
        return ids.get(0);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PerfilIdentificadores(List<Identificador> identificadoresOrigen) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Identificador(String idCliente) { }

    /** El servicio de Perfil no respondió: el movimiento queda FALLIDO y se puede reprocesar. */
    public static class PerfilNoDisponibleException extends RuntimeException {
        public PerfilNoDisponibleException(String mensaje) {
            super(mensaje);
        }
    }
}
