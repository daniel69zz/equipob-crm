package com.maxiconecta.crm.comportamiento.parametro;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
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
            @Value("${comportamiento.inactividad.umbral-dias:90}") int inactividadDias,
            @Value("${comportamiento.nivel-consumo.umbral-medio:500}") BigDecimal umbralMedio,
            @Value("${comportamiento.nivel-consumo.umbral-alto:5000}") BigDecimal umbralAlto) {
        // Reloj propio del sistema: varias pruebas reemplazan el Clock del servicio por uno simulado.
        return new ParametrosGenerales(jdbc, gestor, Clock.systemUTC(), List.of(
                DefinicionParametro.entero(CodigosParametros.INACTIVIDAD_DIAS, "Periodo de inactividad",
                        "Días sin ninguna compra vigente para considerar inactivo a un cliente. Lo usan la lista de "
                                + "clientes inactivos, los clientes activos del panel y el riesgo de abandono.",
                        "días", 7, 730, inactividadDias),
                new DefinicionParametro(CodigosParametros.NIVEL_CONSUMO_UMBRAL_MEDIO, "Umbral de consumo medio",
                        "Valor acumulado de compras vigentes desde el que un cliente es de consumo MEDIO. Por debajo "
                                + "es BAJO.", "Bs", new BigDecimal("1.00"), new BigDecimal("1000000.00"), 2, umbralMedio),
                new DefinicionParametro(CodigosParametros.NIVEL_CONSUMO_UMBRAL_ALTO, "Umbral de consumo alto",
                        "Valor acumulado de compras vigentes desde el que un cliente es de consumo ALTO. Debe ser "
                                + "mayor que el umbral medio.", "Bs", new BigDecimal("2.00"),
                        new BigDecimal("10000000.00"), 2, umbralAlto)),
                ConfiguracionParametros::umbralesOrdenados, eventos);
    }

    /** SCRUM-518: el umbral alto debe superar al medio, si no el nivel MEDIO quedaría vacío. */
    static Map<String, String> umbralesOrdenados(Map<String, BigDecimal> valores) {
        BigDecimal medio = valores.get(CodigosParametros.NIVEL_CONSUMO_UMBRAL_MEDIO);
        BigDecimal alto = valores.get(CodigosParametros.NIVEL_CONSUMO_UMBRAL_ALTO);
        if (medio != null && alto != null && alto.compareTo(medio) <= 0) {
            return Map.of(CodigosParametros.NIVEL_CONSUMO_UMBRAL_ALTO,
                    "debe ser mayor que el umbral de consumo medio (" + medio.toPlainString() + " Bs)");
        }
        return Map.of();
    }
}
