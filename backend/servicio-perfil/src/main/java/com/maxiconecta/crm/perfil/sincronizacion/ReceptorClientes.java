package com.maxiconecta.crm.perfil.sincronizacion;

import com.maxiconecta.crm.perfil.configuracion.ConfiguracionRabbit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Recibe de RabbitMQ los eventos de datos del cliente de Marketplace y Ventas.
 * El contenido se toma como texto para poder anotar en la bitácora incluso los mensajes mal formados.
 */
@Component
public class ReceptorClientes {

    private final SincronizacionClientes sincronizacion;

    public ReceptorClientes(SincronizacionClientes sincronizacion) {
        this.sincronizacion = sincronizacion;
    }

    @RabbitListener(queues = ConfiguracionRabbit.COLA_CLIENTES)
    public void recibir(Message mensaje) {
        sincronizacion.recibir(new String(mensaje.getBody(), StandardCharsets.UTF_8));
    }
}
