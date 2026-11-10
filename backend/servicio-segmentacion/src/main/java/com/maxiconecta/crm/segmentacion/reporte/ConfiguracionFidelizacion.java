package com.maxiconecta.crm.segmentacion.reporte;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Cliente HTTP de servicio-fidelizacion, que da los niveles del reporte de distribución (SCRUM-464).
 * Igual que el de servicio-perfil: va por la red interna, sin pasar por el API Gateway.
 */
@Configuration
public class ConfiguracionFidelizacion {

    @Bean
    @Qualifier("fidelizacion")
    RestClient restClienteFidelizacion(RestClient.Builder builder,
                                       @Value("${crm.fidelizacion.url}") String url,
                                       @Value("${crm.fidelizacion.tiempo-espera}") Duration tiempoEspera) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(tiempoEspera).build();
        JdkClientHttpRequestFactory solicitudes = new JdkClientHttpRequestFactory(http);
        solicitudes.setReadTimeout(tiempoEspera);
        return builder.baseUrl(url).requestFactory(solicitudes).build();
    }
}
