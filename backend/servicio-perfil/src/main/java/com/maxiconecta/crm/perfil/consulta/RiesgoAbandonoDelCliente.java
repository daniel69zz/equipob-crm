package com.maxiconecta.crm.perfil.consulta;

import com.maxiconecta.crm.perfil.cliente.ClienteOrigen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Riesgo de abandono del cliente, consultado en servicio-comportamiento para la ficha integral
 * (SCRUM-325, docs/compra/riesgo-abandono.md). Comportamiento lo calcula por número de cliente del
 * CRM sobre las compras vinculadas a su perfil, así que sin identificadores de origen no hay riesgo
 * que consultar. Si el servicio no responde, el bloque llega sin datos en vez de hacer fallar la
 * ficha (CA2 de SCRUM-10).
 */
@Component
public class RiesgoAbandonoDelCliente {

    private static final Logger log = LoggerFactory.getLogger(RiesgoAbandonoDelCliente.class);

    private final RestClient http;

    public RiesgoAbandonoDelCliente(@Qualifier("comportamiento") RestClient http) {
        this.http = http;
    }

    public RiesgoAbandonoResponse buscar(Long clienteId, List<ClienteOrigen> identificadoresOrigen) {
        if (identificadoresOrigen.isEmpty()) {
            return RiesgoAbandonoResponse.SIN_DATOS;
        }
        try {
            RiesgoAbandonoResponse riesgo = http.get()
                    .uri("/api/comportamiento/clientes/{clienteId}/riesgo-abandono", clienteId)
                    .retrieve().body(RiesgoAbandonoResponse.class);
            if (riesgo == null || riesgo.sinDatos() || riesgo.nivel() == null) {
                return RiesgoAbandonoResponse.SIN_DATOS;
            }
            return new RiesgoAbandonoResponse(riesgo.nivel(), riesgo.puntaje(), riesgo.inactivo(),
                    riesgo.enSeguimientoDesde(), riesgo.calculadoEn(),
                    riesgo.factores() == null ? List.of() : riesgo.factores(), false);
        } catch (RestClientException ex) {
            log.warn("No se pudo consultar el riesgo de abandono del cliente {}; la ficha integral seguirá "
                    + "cargando sin ese bloque ({})", clienteId, ex.getClass().getSimpleName());
            return RiesgoAbandonoResponse.SIN_DATOS;
        }
    }
}
