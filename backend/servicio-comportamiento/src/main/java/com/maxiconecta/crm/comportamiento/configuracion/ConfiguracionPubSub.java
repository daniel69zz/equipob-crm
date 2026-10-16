package com.maxiconecta.crm.comportamiento.configuracion;

import com.google.cloud.spring.core.GcpProjectIdProvider;
import com.google.cloud.spring.pubsub.PubSubAdmin;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.maxiconecta.crm.comportamiento.ingesta.IngestaCompras;
import com.maxiconecta.crm.comportamiento.mensajeria.Mensajeria;
import com.maxiconecta.crm.comportamiento.mensajeria.RecursosPubSub;
import com.maxiconecta.crm.comportamiento.mensajeria.ReinyectorPubSub;
import com.maxiconecta.crm.comportamiento.mensajeria.SuscripcionPubSub;
import com.maxiconecta.crm.comportamiento.mensajeria.SuscriptorPubSub;
import com.maxiconecta.crm.comportamiento.preferencia.ReceptorPreferencias;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Suscripciones de Google Cloud Pub/Sub del servicio (perfil {@code pubsub}, docs/mensajeria/pubsub.md):
 * las mismas colas que {@link ConfiguracionRabbit}, con los mismos nombres y los mismos manejadores.
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
                                             IngestaCompras ingesta, ReceptorPreferencias preferencias) {
        return new SuscriptorPubSub(pubsub, recursos, crear, List.of(
                new SuscripcionPubSub(ConfiguracionRabbit.COLA_COMPRAS, ConfiguracionRabbit.EXCHANGE_VENTAS,
                        ConfiguracionRabbit.RUTA_COMPRA_CONFIRMADA, cuerpo -> ingesta.recibir(texto(cuerpo))),
                new SuscripcionPubSub(ConfiguracionRabbit.COLA_ANULACIONES, ConfiguracionRabbit.EXCHANGE_VENTAS,
                        ConfiguracionRabbit.RUTA_COMPRA_ANULADA, cuerpo -> ingesta.recibirAnulacion(texto(cuerpo))),
                new SuscripcionPubSub(ConfiguracionRabbit.COLA_PREFERENCIAS, ConfiguracionRabbit.EXCHANGE_CRM,
                        ConfiguracionRabbit.RUTA_METRICAS_ACTUALIZADAS, preferencias::procesar)));
    }

    private static String texto(byte[] cuerpo) {
        return new String(cuerpo, StandardCharsets.UTF_8);
    }
}
