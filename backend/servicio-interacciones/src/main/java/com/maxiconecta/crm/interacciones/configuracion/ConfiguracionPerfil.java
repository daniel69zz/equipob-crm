package com.maxiconecta.crm.interacciones.configuracion;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** Cliente HTTP hacia servicio-perfil, con un tiempo de espera acotado. */
@Configuration
public class ConfiguracionPerfil {

    @Bean
    @Qualifier("perfil")
    RestClient restClientePerfil(RestClient.Builder builder,
                                 @Value("${crm.perfil.url}") String url,
                                 @Value("${crm.perfil.tiempo-espera}") Duration tiempoEspera) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(tiempoEspera).build();
        JdkClientHttpRequestFactory solicitudes = new JdkClientHttpRequestFactory(http);
        solicitudes.setReadTimeout(tiempoEspera);
        return builder.baseUrl(url).requestFactory(solicitudes).build();
    }
}
