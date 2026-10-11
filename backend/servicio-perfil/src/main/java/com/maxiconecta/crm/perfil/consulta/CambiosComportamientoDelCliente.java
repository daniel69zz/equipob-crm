package com.maxiconecta.crm.perfil.consulta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.maxiconecta.crm.perfil.cliente.ClienteOrigen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Alertas de cambio de comportamiento del cliente, consultadas en servicio-comportamiento con sus
 * identificadores de origen para la ficha integral (SCRUM-567, docs/compra/cambios-comportamiento.md).
 * Si el servicio no responde, el bloque llega sin datos en vez de hacer fallar la ficha (CA2 de SCRUM-10).
 */
@Component
public class CambiosComportamientoDelCliente {

    private static final Logger log = LoggerFactory.getLogger(CambiosComportamientoDelCliente.class);

    private final RestClient http;

    public CambiosComportamientoDelCliente(@Qualifier("comportamiento") RestClient http) {
        this.http = http;
    }

    public CambiosComportamientoResponse buscar(Long clienteId, List<ClienteOrigen> identificadoresOrigen) {
        if (identificadoresOrigen.isEmpty()) {
            return CambiosComportamientoResponse.SIN_DATOS;
        }
        try {
            Cambios cambios = http.get()
                    .uri(builder -> {
                        builder.path("/api/comportamiento/clientes/{clienteId}/cambios-comportamiento");
                        identificadoresOrigen.forEach(vinculo -> builder.queryParam("identificador", vinculo.getIdClienteOrigen()));
                        return builder.build(clienteId);
                    })
                    .retrieve().body(Cambios.class);
            if (cambios == null) {
                return CambiosComportamientoResponse.SIN_DATOS;
            }
            return new CambiosComportamientoResponse(cambios.estado(), cambios.evaluadoEn(),
                    cambios.alertas() == null ? List.of() : cambios.alertas(), false);
        } catch (RestClientException ex) {
            log.warn("No se pudo consultar los cambios de comportamiento del cliente {}; la ficha integral seguirá "
                    + "cargando sin ese bloque ({})", clienteId, ex.getClass().getSimpleName());
            return CambiosComportamientoResponse.SIN_DATOS;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Cambios(String estado, OffsetDateTime evaluadoEn, List<CambiosComportamientoResponse.Alerta> alertas) {
    }
}
