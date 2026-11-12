package com.maxiconecta.crm.comportamiento.consumo;

import com.maxiconecta.crm.comportamiento.parametro.CodigosParametros;
import com.maxiconecta.crm.comportamiento.parametro.ParametrosGenerales;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Umbrales del nivel de consumo (SCRUM-270, docs/compra/nivel-consumo.md): desde
 * {@code umbralAlto} el cliente es ALTO, desde {@code umbralMedio} MEDIO y por debajo BAJO. Un
 * valor exactamente en un umbral pertenece al nivel superior. Sin valor vigente no hay nivel.
 *
 * <p>Los umbrales son parámetros generales del CRM (SCRUM-518): se leen en cada clasificación, así que un cambio
 * rige desde que el Administrador de CRM lo guarda.
 */
@Component
public class CriterioNivelConsumo {

    private final Supplier<BigDecimal> umbralMedio;
    private final Supplier<BigDecimal> umbralAlto;

    @Autowired
    public CriterioNivelConsumo(ParametrosGenerales parametros) {
        this.umbralMedio = () -> parametros.valor(CodigosParametros.NIVEL_CONSUMO_UMBRAL_MEDIO);
        this.umbralAlto = () -> parametros.valor(CodigosParametros.NIVEL_CONSUMO_UMBRAL_ALTO);
    }

    /** Con umbrales fijos, para las pruebas. */
    public CriterioNivelConsumo(BigDecimal umbralMedio, BigDecimal umbralAlto) {
        if (umbralMedio.signum() <= 0 || umbralAlto.compareTo(umbralMedio) <= 0) {
            throw new IllegalStateException("Los umbrales del nivel de consumo no son válidos: el medio ("
                    + umbralMedio.toPlainString() + ") debe ser mayor que cero y menor que el alto ("
                    + umbralAlto.toPlainString() + ")");
        }
        this.umbralMedio = () -> umbralMedio;
        this.umbralAlto = () -> umbralAlto;
    }

    public BigDecimal umbralMedio() {
        return umbralMedio.get();
    }

    public BigDecimal umbralAlto() {
        return umbralAlto.get();
    }

    /** Nivel que corresponde a un valor acumulado; vacío cuando no hay nada vigente que clasificar. */
    public Optional<NivelConsumo> clasificar(BigDecimal valorAcumulado) {
        if (valorAcumulado == null || valorAcumulado.signum() <= 0) {
            return Optional.empty();
        }
        if (valorAcumulado.compareTo(umbralAlto()) >= 0) {
            return Optional.of(NivelConsumo.ALTO);
        }
        if (valorAcumulado.compareTo(umbralMedio()) >= 0) {
            return Optional.of(NivelConsumo.MEDIO);
        }
        return Optional.of(NivelConsumo.BAJO);
    }
}
