package com.maxiconecta.crm.comportamiento.configuracion;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Colas de RabbitMQ del servicio. Los nombres siguen el contrato
 * docs/contratos-eventos/RIO-CRM-02-compra-confirmada.md.
 */
@Configuration
public class ConfiguracionRabbit {

    public static final String EXCHANGE_VENTAS = "ventas.eventos";
    public static final String RUTA_COMPRA_CONFIRMADA = "compra.confirmada";
    public static final String COLA_COMPRAS = "crm.comportamiento.compras";

    @Bean
    public TopicExchange exchangeVentas() {
        return new TopicExchange(EXCHANGE_VENTAS, true, false);
    }

    @Bean
    public Queue colaCompras() {
        return QueueBuilder.durable(COLA_COMPRAS).build();
    }

    @Bean
    public Binding enlaceCompras(Queue colaCompras, TopicExchange exchangeVentas) {
        return BindingBuilder.bind(colaCompras).to(exchangeVentas).with(RUTA_COMPRA_CONFIRMADA);
    }
}
