package com.maxiconecta.crm.fidelizacion.configuracion;

import com.google.cloud.spring.core.GcpProjectIdProvider;
import com.google.cloud.spring.pubsub.PubSubAdmin;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.maxiconecta.crm.fidelizacion.mensajeria.Mensajeria;
import com.maxiconecta.crm.fidelizacion.mensajeria.RecursosPubSub;
import com.maxiconecta.crm.fidelizacion.mensajeria.SuscripcionPubSub;
import com.maxiconecta.crm.fidelizacion.mensajeria.SuscriptorPubSub;
import com.maxiconecta.crm.fidelizacion.movimiento.IngestaMovimientos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Suscripción de Google Cloud Pub/Sub a los movimientos de puntos (perfil {@code pubsub},
 * docs/mensajeria/pubsub.md): la misma cola que {@link ConfiguracionRabbit}, con el mismo nombre y el
 * mismo tratamiento.
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
                                             IngestaMovimientos ingesta) {
        return new SuscriptorPubSub(pubsub, recursos, crear, List.of(new SuscripcionPubSub(
                ConfiguracionRabbit.COLA_MOVIMIENTOS, ConfiguracionRabbit.EXCHANGE_VENTAS,
                ConfiguracionRabbit.RUTA_MOVIMIENTO_PUNTOS,
                cuerpo -> ingesta.recibir(new String(cuerpo, StandardCharsets.UTF_8)))));
    }
}
