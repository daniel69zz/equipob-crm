package com.maxiconecta.crm.comportamiento.sugerencia;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Umbrales de la sugerencia de categorías afines (SCRUM-380, docs/preferencias/sugerencia-categorias.md).
 * Un valor inválido impide que el servicio arranque.
 */
@ConfigurationProperties("comportamiento.sugerencias")
public record ReglaSugerencias(int minimoClientes, BigDecimal afinidadMinima, int maximo) {
    public ReglaSugerencias {
        if (minimoClientes < 1) {
            throw new IllegalArgumentException("comportamiento.sugerencias.minimo-clientes debe ser al menos 1");
        }
        if (afinidadMinima == null || afinidadMinima.signum() < 0 || afinidadMinima.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("comportamiento.sugerencias.afinidad-minima debe estar entre 0 y 1");
        }
        if (maximo < 1) {
            throw new IllegalArgumentException("comportamiento.sugerencias.maximo debe ser al menos 1");
        }
    }
}
