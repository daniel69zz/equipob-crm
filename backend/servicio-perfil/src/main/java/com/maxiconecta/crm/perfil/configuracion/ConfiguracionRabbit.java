package com.maxiconecta.crm.perfil.configuracion;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Colas de RabbitMQ del servicio. Los nombres siguen el contrato
 * docs/contratos-eventos/RIO-CRM-01-datos-cliente.md.
 * <p>
 * Los errores de procesamiento se resuelven en la bitácora de sincronización. Solo si ni siquiera
 * se puede escribir en la bitácora, el mensaje se reintenta y después pasa a la cola de respaldo.
 */
@Configuration
public class ConfiguracionRabbit {

    public static final String EXCHANGE_VENTAS = "ventas.eventos";
    public static final String RUTA_CLIENTES = "cliente.*";
    public static final String RUTA_CLIENTE_REGISTRADO = "cliente.registrado";
    public static final String RUTA_CLIENTE_ACTUALIZADO = "cliente.actualizado";
    public static final String COLA_CLIENTES = "crm.perfil.clientes";
    public static final String EXCHANGE_RESPALDO = "crm.perfil.respaldo";
    public static final String COLA_CLIENTES_RESPALDO = "crm.perfil.clientes.respaldo";

    @Bean
    public TopicExchange exchangeVentas() {
        return new TopicExchange(EXCHANGE_VENTAS, true, false);
    }

    @Bean
    public Queue colaClientes() {
        return QueueBuilder.durable(COLA_CLIENTES)
                .deadLetterExchange(EXCHANGE_RESPALDO)
                .deadLetterRoutingKey(COLA_CLIENTES_RESPALDO)
                .build();
    }

    @Bean
    public Binding enlaceClientes(Queue colaClientes, TopicExchange exchangeVentas) {
        return BindingBuilder.bind(colaClientes).to(exchangeVentas).with(RUTA_CLIENTES);
    }

    @Bean
    public DirectExchange exchangeRespaldo() {
        return new DirectExchange(EXCHANGE_RESPALDO, true, false);
    }

    @Bean
    public Queue colaClientesRespaldo() {
        return QueueBuilder.durable(COLA_CLIENTES_RESPALDO).build();
    }

    @Bean
    public Binding enlaceClientesRespaldo(Queue colaClientesRespaldo, DirectExchange exchangeRespaldo) {
        return BindingBuilder.bind(colaClientesRespaldo).to(exchangeRespaldo).with(COLA_CLIENTES_RESPALDO);
    }
}
