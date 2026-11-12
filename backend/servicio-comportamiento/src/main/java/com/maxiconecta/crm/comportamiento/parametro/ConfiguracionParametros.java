package com.maxiconecta.crm.comportamiento.parametro;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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
    public ParametrosGenerales parametrosGenerales(
            JdbcTemplate jdbc, PlatformTransactionManager gestor, ApplicationEventPublisher eventos,
            @Value("${comportamiento.inactividad.umbral-dias:90}") int inactividadDias) {
        // Reloj propio del sistema: varias pruebas reemplazan el Clock del servicio por uno simulado.
        return new ParametrosGenerales(jdbc, gestor, Clock.systemUTC(), List.of(
                DefinicionParametro.entero(CodigosParametros.INACTIVIDAD_DIAS, "Periodo de inactividad",
                        "Días sin ninguna compra vigente para considerar inactivo a un cliente. Lo usan la lista de "
                                + "clientes inactivos, los clientes activos del panel y el riesgo de abandono.",
                        "días", 7, 730, inactividadDias)),
                valores -> Map.of(), eventos);
    }
}
