package com.maxiconecta.crm.segmentacion.consulta;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/** Una sola fuente de umbrales: Comportamiento. Sin valores alternativos ni caché obsoleta. */
@Component
public class UmbralesConsumo {
    private final RestClient http;

    public UmbralesConsumo(@Value("${crm.comportamiento.url:http://localhost:8082}") String url) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.http = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public Valores obtener() {
        try {
            Valores valores = http.get().uri("/internal/consumo/umbrales").retrieve().body(Valores.class);
            if (valores == null || valores.medio() == null || valores.alto() == null
                    || valores.medio().signum() <= 0 || valores.alto().compareTo(valores.medio()) <= 0) {
                throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Los umbrales de consumo no están disponibles");
            }
            return valores;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                    "No se pudo consultar el nivel de consumo. Intente nuevamente.", ex);
        }
    }

    public record Valores(BigDecimal medio, BigDecimal alto) { }
}
