package com.maxiconecta.crm.segmentacion.consulta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Nombres de los clientes de una página del listado, pedidos a servicio-perfil en una sola llamada
 * (SCRUM-277, {@code GET /api/perfil/clientes?id=..}). Si perfil no responde, el listado se muestra
 * igual sin nombres, como la ficha integral ante un servicio caído.
 */
@Component
public class PerfilesClientes {

    private static final Logger log = LoggerFactory.getLogger(PerfilesClientes.class);

    private final RestClient http;

    public PerfilesClientes(@Qualifier("perfil") RestClient http) {
        this.http = http;
    }

    public NombresClientes buscar(Collection<Long> clienteIds) {
        if (clienteIds.isEmpty()) {
            return new NombresClientes(Map.of(), true);
        }
        try {
            Perfil[] perfiles = http.get()
                    .uri(builder -> {
                        builder.path("/api/perfil/clientes");
                        clienteIds.forEach(id -> builder.queryParam("id", id));
                        return builder.build();
                    })
                    .retrieve().body(Perfil[].class);
            Map<Long, Nombre> nombres = perfiles == null ? Map.of() : Arrays.stream(perfiles)
                    .filter(perfil -> perfil.id() != null)
                    .collect(Collectors.toMap(Perfil::id, perfil -> new Nombre(perfil.nombres(), perfil.apellidos()),
                            (primero, repetido) -> primero));
            return new NombresClientes(nombres, true);
        } catch (RestClientException ex) {
            log.warn("No se pudieron consultar los nombres de {} clientes; el listado se mostrará sin ellos ({})",
                    clienteIds.size(), ex.getClass().getSimpleName());
            return new NombresClientes(Map.of(), false);
        }
    }

    public record Nombre(String nombres, String apellidos) {

        static final Nombre DESCONOCIDO = new Nombre(null, null);
    }

    /** {@code disponibles} es falso cuando servicio-perfil no respondió. */
    public record NombresClientes(Map<Long, Nombre> nombres, boolean disponibles) {

        public Nombre de(long clienteId) {
            return nombres.getOrDefault(clienteId, Nombre.DESCONOCIDO);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Perfil(Long id, String nombres, String apellidos) {
    }
}
