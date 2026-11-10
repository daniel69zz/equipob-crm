package com.maxiconecta.crm.segmentacion.reporte;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * Indicadores del panel que no guarda la segmentación (SCRUM-471): los clientes activos, de
 * servicio-comportamiento (SCRUM-475), y los puntos acumulados, de servicio-fidelizacion (SCRUM-476). Son
 * consultas internas por la red de servicios. Si uno no responde, su indicador queda vacío y el panel se
 * muestra igual con los demás, como el reporte de distribución ante fidelización caída.
 */
@Component
public class IndicadoresExternos {

    private static final Logger log = LoggerFactory.getLogger(IndicadoresExternos.class);

    private final RestClient comportamiento;
    private final RestClient fidelizacion;

    public IndicadoresExternos(@Qualifier("comportamiento") RestClient comportamiento,
                               @Qualifier("fidelizacion") RestClient fidelizacion) {
        this.comportamiento = comportamiento;
        this.fidelizacion = fidelizacion;
    }

    public Optional<ClientesActivos> clientesActivos() {
        try {
            return Optional.ofNullable(comportamiento.get().uri("/api/comportamiento/reportes/clientes-activos")
                    .retrieve().body(ClientesActivos.class));
        } catch (RestClientException ex) {
            log.warn("No se pudieron consultar los clientes activos; el panel se mostrará sin ese indicador ({})",
                    ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public Optional<PuntosAcumulados> puntosAcumulados() {
        try {
            return Optional.ofNullable(fidelizacion.get().uri("/api/fidelizacion/reportes/puntos")
                    .retrieve().body(PuntosAcumulados.class));
        } catch (RestClientException ex) {
            log.warn("No se pudieron consultar los puntos acumulados; el panel se mostrará sin ese indicador ({})",
                    ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClientesActivos(long activos, int umbralDias) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PuntosAcumulados(long puntos, long clientesConPuntos) {
    }
}
