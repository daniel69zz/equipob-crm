package com.maxiconecta.crm.perfil.configuracion;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Reintentos ante errores técnicos al aplicar un evento al perfil (docs/perfil/mapeo-datos-perfil.md).
 *
 * @param maximo         intentos en total, incluido el primero
 * @param esperaInicial  espera antes del segundo intento
 * @param multiplicador  factor por el que crece la espera en cada reintento
 */
@ConfigurationProperties(prefix = "crm.perfil.reintentos")
public record PoliticaReintentos(Integer maximo, Duration esperaInicial, Double multiplicador) {

    public PoliticaReintentos {
        maximo = maximo == null || maximo < 1 ? 3 : maximo;
        esperaInicial = esperaInicial == null ? Duration.ofMillis(500) : esperaInicial;
        multiplicador = multiplicador == null || multiplicador < 1 ? 2.0 : multiplicador;
    }

    /** Espera antes del intento siguiente al número {@code intento} (1 = después del primero). */
    public Duration esperaTras(int intento) {
        return Duration.ofMillis(Math.round(esperaInicial.toMillis() * Math.pow(multiplicador, intento - 1)));
    }
}
