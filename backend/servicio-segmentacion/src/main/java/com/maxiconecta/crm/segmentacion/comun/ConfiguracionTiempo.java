package com.maxiconecta.crm.segmentacion.comun;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Reloj del servicio, reemplazable en pruebas. Lo usa el reporte de distribución de clientes para
 * fechar cuándo se generó (SCRUM-57), igual que {@code configuracion.ConfiguracionTiempo} en
 * servicio-comportamiento.
 */
@Configuration
public class ConfiguracionTiempo {

    @Bean
    public Clock relojSistema() {
        return Clock.systemUTC();
    }
}
