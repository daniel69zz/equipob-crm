package com.maxiconecta.crm.perfil.configuracion;

import com.google.cloud.spring.core.GcpProjectIdProvider;
import com.google.cloud.spring.pubsub.PubSubAdmin;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.maxiconecta.crm.perfil.mensajeria.Mensajeria;
import com.maxiconecta.crm.perfil.mensajeria.RecursosPubSub;
import com.maxiconecta.crm.perfil.mensajeria.SuscripcionPubSub;
import com.maxiconecta.crm.perfil.mensajeria.SuscriptorPubSub;
import com.maxiconecta.crm.perfil.sincronizacion.SincronizacionClientes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Suscripción de Google Cloud Pub/Sub a los eventos de datos del cliente (perfil {@code pubsub},
 * docs/mensajeria/pubsub.md): la misma cola que {@link ConfiguracionRabbit}, con el mismo nombre y el
 * mismo tratamiento. Recibe toda ruta {@code cliente.*}, como el enlace del exchange topic.
 */
@Configuration
@ConditionalOnProperty(name = Mensajeria.PROPIEDAD_BROKER, havingValue = Mensajeria.PUBSUB)
public class ConfiguracionPubSub {

    @Bean
    public RecursosPubSub recursosPubSub(PubSubAdmin admin, GcpProjectIdProvider proyecto,
                                         @Value("${crm.mensajeria.pubsub.max-intentos:5}") int maxIntentos) {
        return new RecursosPubSub(admin, proyecto, maxIntentos);
    }

    @Bean
    public SuscriptorPubSub suscriptorPubSub(PubSubTemplate pubsub, RecursosPubSub recursos,
                                             @Value("${crm.mensajeria.pubsub.crear-recursos:true}") boolean crear,
                                             SincronizacionClientes sincronizacion) {
        return new SuscriptorPubSub(pubsub, recursos, crear, List.of(new SuscripcionPubSub(
                ConfiguracionRabbit.COLA_CLIENTES, ConfiguracionRabbit.EXCHANGE_VENTAS, ConfiguracionRabbit.RUTA_CLIENTES,
                cuerpo -> sincronizacion.recibir(new String(cuerpo, StandardCharsets.UTF_8)))));
    }
}
