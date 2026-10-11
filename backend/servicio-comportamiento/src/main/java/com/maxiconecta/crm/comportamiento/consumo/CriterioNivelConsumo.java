package com.maxiconecta.crm.comportamiento.consumo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Umbrales del nivel de consumo (SCRUM-270, docs/compra/nivel-consumo.md): desde
 * {@code umbralAlto} el cliente es ALTO, desde {@code umbralMedio} MEDIO y por debajo BAJO. Un
 * valor exactamente en un umbral pertenece al nivel superior. Sin valor vigente no hay nivel.
 */
@Component
public class CriterioNivelConsumo {

    private final BigDecimal umbralMedio;
    private final BigDecimal umbralAlto;

    public CriterioNivelConsumo(@Value("${comportamiento.nivel-consumo.umbral-medio:500}") BigDecimal umbralMedio,
                                @Value("${comportamiento.nivel-consumo.umbral-alto:5000}") BigDecimal umbralAlto) {
        if (umbralMedio.signum() <= 0 || umbralAlto.compareTo(umbralMedio) <= 0) {
            throw new IllegalStateException("Los umbrales del nivel de consumo no son válidos: el medio ("
                    + umbralMedio.toPlainString() + ") debe ser mayor que cero y menor que el alto ("
                    + umbralAlto.toPlainString() + ")");
        }
        this.umbralMedio = umbralMedio;
        this.umbralAlto = umbralAlto;
    }

    public BigDecimal umbralMedio() {
        return umbralMedio;
    }

    public BigDecimal umbralAlto() {
        return umbralAlto;
    }

    /** Nivel que corresponde a un valor acumulado; vacío cuando no hay nada vigente que clasificar. */
    public Optional<NivelConsumo> clasificar(BigDecimal valorAcumulado) {
        if (valorAcumulado == null || valorAcumulado.signum() <= 0) {
            return Optional.empty();
        }
        if (valorAcumulado.compareTo(umbralAlto) >= 0) {
            return Optional.of(NivelConsumo.ALTO);
        }
        if (valorAcumulado.compareTo(umbralMedio) >= 0) {
            return Optional.of(NivelConsumo.MEDIO);
        }
        return Optional.of(NivelConsumo.BAJO);
    }
}
