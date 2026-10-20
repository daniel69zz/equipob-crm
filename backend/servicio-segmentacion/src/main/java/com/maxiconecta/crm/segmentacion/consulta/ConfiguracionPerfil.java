package com.maxiconecta.crm.segmentacion.consulta;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** Cliente HTTP de servicio-perfil, que da el nombre de los clientes del listado (SCRUM-277). */
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
