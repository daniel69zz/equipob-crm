package com.maxiconecta.crm.fidelizacion.parametro;

import com.maxiconecta.crm.fidelizacion.caducidad.ReglaCaducidad;
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
 * Los parámetros generales de servicio-fidelizacion (SCRUM-65). La primera vez toman el valor de la configuración
 * del servicio (variables de entorno); desde ahí rigen los de la base, que cambia el Administrador de CRM.
 */
@Configuration
public class ConfiguracionParametros {

    @Bean(initMethod = "inicializar")
    public ParametrosGenerales parametrosGenerales(
            JdbcTemplate jdbc, PlatformTransactionManager gestor, ApplicationEventPublisher eventos,
            AuditoriaParametros auditoria,
            @Value("${fidelizacion.puntos.vigencia-meses:12}") int vigenciaMeses) {
        return new ParametrosGenerales(jdbc, gestor, Clock.systemUTC(), List.of(
                DefinicionParametro.entero(CodigosParametros.PUNTOS_VIGENCIA_MESES, "Vigencia de los puntos",
                        "Meses que dura el saldo de puntos de un cliente desde su última compra que acumuló puntos. "
                                + "La usan la caducidad diaria y las consultas de saldo, también la de Marketplace y "
                                + "Ventas.", "meses", ReglaCaducidad.VIGENCIA_MINIMA, ReglaCaducidad.VIGENCIA_MAXIMA,
                        vigenciaMeses)),
                valores -> Map.of(), auditoria::registrar, eventos);
    }
}
