package com.maxiconecta.crm.comportamiento.parametro;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * Los parámetros generales de servicio-comportamiento (SCRUM-65). La primera vez toman el valor de la configuración
 * del servicio (variables de entorno); desde ahí rigen los de la base, que cambia el Administrador de CRM.
 */
@Configuration
public class ConfiguracionParametros {

    @Bean(initMethod = "inicializar")
    public ParametrosGenerales parametrosGenerales(JdbcTemplate jdbc, PlatformTransactionManager gestor, Clock reloj) {
        return new ParametrosGenerales(jdbc, gestor, reloj, List.of(), valores -> Map.of());
    }
}
