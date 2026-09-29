package com.maxiconecta.crm.perfil.configuracion;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class ConfiguracionComportamiento {

    @Bean
    @Qualifier("comportamiento")
    RestClient restClienteComportamiento(RestClient.Builder builder,
                                         @Value("${crm.comportamiento.url}") String url,
                                         @Value("${crm.comportamiento.tiempo-espera}") Duration tiempoEspera) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(tiempoEspera).build();
        JdkClientHttpRequestFactory solicitudes = new JdkClientHttpRequestFactory(http);
        solicitudes.setReadTimeout(tiempoEspera);
        return builder.baseUrl(url).requestFactory(solicitudes).build();
    }
}
