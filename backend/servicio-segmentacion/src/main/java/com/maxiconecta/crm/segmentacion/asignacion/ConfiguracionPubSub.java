package com.maxiconecta.crm.segmentacion.asignacion;

import com.google.cloud.spring.core.GcpProjectIdProvider;
import com.google.cloud.spring.pubsub.PubSubAdmin;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.maxiconecta.crm.segmentacion.mensajeria.Mensajeria;
import com.maxiconecta.crm.segmentacion.mensajeria.RecursosPubSub;
import com.maxiconecta.crm.segmentacion.mensajeria.ReinyectorPubSub;
import com.maxiconecta.crm.segmentacion.mensajeria.SuscripcionPubSub;
import com.maxiconecta.crm.segmentacion.mensajeria.SuscriptorPubSub;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Suscripción de Google Cloud Pub/Sub a las métricas de los clientes (perfil {@code pubsub},
 * docs/mensajeria/pubsub.md): la misma cola que {@link ConfiguracionRabbit}, con el mismo nombre y el
 * mismo receptor.
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
    public ReinyectorPubSub reinyectorPubSub(PubSubTemplate pubsub) {
        return new ReinyectorPubSub(pubsub);
    }

    @Bean
    public SuscriptorPubSub suscriptorPubSub(PubSubTemplate pubsub, RecursosPubSub recursos,
                                             @Value("${crm.mensajeria.pubsub.crear-recursos:true}") boolean crear,
                                             ReceptorMetricas receptor) {
        return new SuscriptorPubSub(pubsub, recursos, crear, List.of(new SuscripcionPubSub(
                ConfiguracionRabbit.COLA_METRICAS, ConfiguracionRabbit.EXCHANGE_CRM,
                ConfiguracionRabbit.RUTA_METRICAS_ACTUALIZADAS, receptor::procesar)));
    }
}
