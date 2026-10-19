package com.maxiconecta.crm.fidelizacion.configuracion;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Colas de RabbitMQ del servicio (SCRUM-388). Los nombres siguen el contrato
 * docs/contratos-eventos/RIO-CRM-03-movimiento-puntos.md.
 * <p>
 * Los errores de procesamiento se resuelven en la bitácora de movimientos (estado FALLIDO). Solo si ni
 * siquiera se puede escribir en la bitácora, el mensaje se reintenta y después pasa a la cola de respaldo.
 */
@Configuration
public class ConfiguracionRabbit {

    public static final String EXCHANGE_VENTAS = "ventas.eventos";
    public static final String RUTA_MOVIMIENTO_PUNTOS = "puntos.movimiento";
    public static final String COLA_MOVIMIENTOS = "crm.fidelizacion.movimientos";
    public static final String EXCHANGE_RESPALDO = "crm.fidelizacion.respaldo";
    public static final String COLA_MOVIMIENTOS_RESPALDO = "crm.fidelizacion.movimientos.respaldo";

    @Bean
    public TopicExchange exchangeVentas() {
        return new TopicExchange(EXCHANGE_VENTAS, true, false);
    }

    @Bean
    public Queue colaMovimientos() {
        return QueueBuilder.durable(COLA_MOVIMIENTOS)
                .deadLetterExchange(EXCHANGE_RESPALDO)
                .deadLetterRoutingKey(COLA_MOVIMIENTOS_RESPALDO)
                .build();
    }

    @Bean
    public Binding enlaceMovimientos(Queue colaMovimientos, TopicExchange exchangeVentas) {
        return BindingBuilder.bind(colaMovimientos).to(exchangeVentas).with(RUTA_MOVIMIENTO_PUNTOS);
    }

    @Bean
    public DirectExchange exchangeRespaldo() {
        return new DirectExchange(EXCHANGE_RESPALDO, true, false);
    }

    @Bean
    public Queue colaMovimientosRespaldo() {
        return QueueBuilder.durable(COLA_MOVIMIENTOS_RESPALDO).build();
    }

    @Bean
    public Binding enlaceMovimientosRespaldo(Queue colaMovimientosRespaldo, DirectExchange exchangeRespaldo) {
        return BindingBuilder.bind(colaMovimientosRespaldo).to(exchangeRespaldo).with(COLA_MOVIMIENTOS_RESPALDO);
    }
}
