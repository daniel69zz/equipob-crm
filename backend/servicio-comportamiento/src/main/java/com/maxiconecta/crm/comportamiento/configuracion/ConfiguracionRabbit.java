package com.maxiconecta.crm.comportamiento.configuracion;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Colas de RabbitMQ del servicio. Los nombres siguen los contratos
 * docs/contratos-eventos/RIO-CRM-02-compra-confirmada.md y RIO-CRM-05-anulacion-compra.md.
 * <p>
 * Los errores de procesamiento se resuelven en la bitácora (estado FALLIDO). Solo si ni siquiera
 * se puede escribir en la bitácora (por ejemplo, la base no responde), el mensaje se reintenta y,
 * agotados los reintentos, pasa a la cola de respaldo para no perderse.
 */
@Configuration
public class ConfiguracionRabbit {

    public static final String EXCHANGE_VENTAS = "ventas.eventos";
    public static final String RUTA_COMPRA_CONFIRMADA = "compra.confirmada";
    public static final String COLA_COMPRAS = "crm.comportamiento.compras";
    public static final String EXCHANGE_RESPALDO = "crm.comportamiento.respaldo";
    public static final String COLA_COMPRAS_RESPALDO = "crm.comportamiento.compras.respaldo";
    public static final String RUTA_COMPRA_ANULADA = "compra.anulada";
    public static final String COLA_ANULACIONES = "crm.comportamiento.anulaciones";

    @Bean
    public TopicExchange exchangeVentas() {
        return new TopicExchange(EXCHANGE_VENTAS, true, false);
    }

    @Bean
    public Queue colaCompras() {
        return QueueBuilder.durable(COLA_COMPRAS)
                .deadLetterExchange(EXCHANGE_RESPALDO)
                .deadLetterRoutingKey(COLA_COMPRAS_RESPALDO)
                .build();
    }

    @Bean
    public DirectExchange exchangeRespaldo() {
        return new DirectExchange(EXCHANGE_RESPALDO, true, false);
    }

    @Bean
    public Queue colaComprasRespaldo() {
        return QueueBuilder.durable(COLA_COMPRAS_RESPALDO).build();
    }

    @Bean
    public Binding enlaceComprasRespaldo(Queue colaComprasRespaldo, DirectExchange exchangeRespaldo) {
        return BindingBuilder.bind(colaComprasRespaldo).to(exchangeRespaldo).with(COLA_COMPRAS_RESPALDO);
    }

    @Bean
    public Binding enlaceCompras(Queue colaCompras, TopicExchange exchangeVentas) {
        return BindingBuilder.bind(colaCompras).to(exchangeVentas).with(RUTA_COMPRA_CONFIRMADA);
    }

    @Bean
    public Queue colaAnulaciones() {
        return QueueBuilder.durable(COLA_ANULACIONES).build();
    }

    @Bean
    public Binding enlaceAnulaciones(Queue colaAnulaciones, TopicExchange exchangeVentas) {
        return BindingBuilder.bind(colaAnulaciones).to(exchangeVentas).with(RUTA_COMPRA_ANULADA);
    }
}
