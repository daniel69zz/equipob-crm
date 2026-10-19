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
 * Clasificación por frecuencia del cliente, consultada en servicio-comportamiento con sus
 * identificadores de origen para la ficha integral (SCRUM-338, docs/compra/clasificacion-frecuencia.md).
 * Si el servicio no responde, se trata igual que "sin clasificación" en vez de hacer fallar la ficha
 * (CA2 de SCRUM-10).
 */
@Component
public class ClasificacionFrecuenciaDelCliente {

    private static final Logger log = LoggerFactory.getLogger(ClasificacionFrecuenciaDelCliente.class);

    private final RestClient http;

    public ClasificacionFrecuenciaDelCliente(@Qualifier("comportamiento") RestClient http) {
        this.http = http;
    }

    public ClasificacionFrecuenciaResponse buscar(Long clienteId, List<ClienteOrigen> identificadoresOrigen) {
        if (identificadoresOrigen.isEmpty()) {
            return ClasificacionFrecuenciaResponse.SIN_DATOS;
        }
        try {
            Indicadores indicadores = http.get()
                    .uri(builder -> {
                        builder.path("/api/comportamiento/clientes/{clienteId}/indicadores");
                        identificadoresOrigen.forEach(vinculo -> builder.queryParam("identificador", vinculo.getIdClienteOrigen()));
                        return builder.build(clienteId);
                    })
                    .retrieve().body(Indicadores.class);
            if (indicadores == null || indicadores.clasificacionFrecuencia() == null
                    || indicadores.clasificacionFrecuencia().sinClasificacion()
                    || indicadores.clasificacionFrecuencia().clasificacion() == null) {
                return ClasificacionFrecuenciaResponse.SIN_DATOS;
            }
            return new ClasificacionFrecuenciaResponse(indicadores.clasificacionFrecuencia().clasificacion(),
                    indicadores.clasificacionFrecuencia().frecuencia(), false);
        } catch (RestClientException ex) {
            log.warn("No se pudo consultar la clasificación por frecuencia del cliente {}; la ficha integral "
                    + "seguirá cargando sin ese bloque ({})", clienteId, ex.getClass().getSimpleName());
            return ClasificacionFrecuenciaResponse.SIN_DATOS;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Indicadores(Clasificacion clasificacionFrecuencia) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Clasificacion(String clasificacion, long frecuencia, boolean sinClasificacion) {
    }
}
