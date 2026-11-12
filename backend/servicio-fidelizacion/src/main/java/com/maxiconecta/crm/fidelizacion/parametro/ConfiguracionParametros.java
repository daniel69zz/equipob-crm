package com.maxiconecta.crm.fidelizacion.parametro;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * Los parámetros generales de servicio-fidelizacion (SCRUM-65). La primera vez toman el valor de la configuración
 * del servicio (variables de entorno); desde ahí rigen los de la base, que cambia el Administrador de CRM.
 */
@Configuration
public class ConfiguracionParametros {

    @Bean(initMethod = "inicializar")
    public ParametrosGenerales parametrosGenerales(JdbcTemplate jdbc, PlatformTransactionManager gestor) {
        return new ParametrosGenerales(jdbc, gestor, Clock.systemUTC(), List.of(), valores -> Map.of());
    }
}
