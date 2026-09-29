package com.maxiconecta.crm.comportamiento.configuracion;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Reloj del servicio, reemplazable en pruebas para calcular indicadores temporales. */
@Configuration
public class ConfiguracionTiempo {

    @Bean
    public Clock relojSistema() {
        return Clock.systemUTC();
    }
}
