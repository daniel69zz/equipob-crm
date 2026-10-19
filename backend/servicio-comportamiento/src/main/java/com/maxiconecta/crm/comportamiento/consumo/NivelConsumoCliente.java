package com.maxiconecta.crm.comportamiento.consumo;

import com.maxiconecta.crm.comportamiento.indicador.ValorAcumulado;

import java.math.BigDecimal;

/**
 * Nivel de consumo de un cliente (docs/compra/nivel-consumo.md, SCRUM-27): el nivel con el valor
 * acumulado que lo origina. {@code sinNivel} avisa que el cliente no tiene compras vigentes que
 * clasificar; en ese caso {@code nivel} es {@code null} y no hay error.
 */
public record NivelConsumoCliente(NivelConsumo nivel, BigDecimal valorAcumulado, boolean sinNivel) {

    public static NivelConsumoCliente de(ValorAcumulado valor, CriterioNivelConsumo criterio) {
        return criterio.clasificar(valor.valor())
                .map(nivel -> new NivelConsumoCliente(nivel, valor.valor(), false))
                .orElseGet(() -> new NivelConsumoCliente(null, BigDecimal.ZERO, true));
    }
}
