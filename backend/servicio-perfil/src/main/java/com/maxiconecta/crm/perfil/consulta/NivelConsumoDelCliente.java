package com.maxiconecta.crm.perfil.consulta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.maxiconecta.crm.perfil.cliente.ClienteOrigen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Nivel de consumo del cliente, consultado en servicio-comportamiento con sus identificadores de
 * origen para la ficha integral (SCRUM-566, docs/compra/nivel-consumo.md). Si el servicio no
 * responde, se trata igual que "sin nivel" en vez de hacer fallar la ficha (CA2 de SCRUM-10).
 */
@Component
public class NivelConsumoDelCliente {

    private static final Logger log = LoggerFactory.getLogger(NivelConsumoDelCliente.class);

    private final RestClient http;

    public NivelConsumoDelCliente(@Qualifier("comportamiento") RestClient http) {
        this.http = http;
    }

    public NivelConsumoResponse buscar(Long clienteId, List<ClienteOrigen> identificadoresOrigen) {
        if (identificadoresOrigen.isEmpty()) {
            return NivelConsumoResponse.SIN_DATOS;
        }
        try {
            Indicadores indicadores = http.get()
                    .uri(builder -> {
                        builder.path("/api/comportamiento/clientes/{clienteId}/indicadores");
                        identificadoresOrigen.forEach(vinculo -> builder.queryParam("identificador", vinculo.getIdClienteOrigen()));
                        return builder.build(clienteId);
                    })
                    .retrieve().body(Indicadores.class);
            if (indicadores == null || indicadores.nivelConsumo() == null || indicadores.nivelConsumo().sinNivel()) {
                return NivelConsumoResponse.SIN_DATOS;
            }
            return new NivelConsumoResponse(indicadores.nivelConsumo().nivel(),
                    indicadores.nivelConsumo().valorAcumulado(), false);
        } catch (RestClientException ex) {
            log.warn("No se pudo consultar el nivel de consumo del cliente {}; la ficha integral seguirá "
                    + "cargando sin ese bloque ({})", clienteId, ex.getClass().getSimpleName());
            return NivelConsumoResponse.SIN_DATOS;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Indicadores(Nivel nivelConsumo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Nivel(String nivel, BigDecimal valorAcumulado, boolean sinNivel) {
    }
}
