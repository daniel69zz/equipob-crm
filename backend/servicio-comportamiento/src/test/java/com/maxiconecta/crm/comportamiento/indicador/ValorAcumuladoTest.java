package com.maxiconecta.crm.comportamiento.indicador;

import com.maxiconecta.crm.comportamiento.compra.ResumenCompras;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-308 · Cálculo del valor acumulado: la suma de lo vigente de las compras del cliente
 * (docs/compra/valor-acumulado.md).
 */
class ValorAcumuladoTest {

    @Test
    void esLaSumaDeLosMontosVigentes() {
        ValorAcumulado valor = ValorAcumulado.de(new ResumenCompras(3, new BigDecimal("600.00")));

        assertThat(valor.valor()).isEqualByComparingTo("600.00");
        assertThat(valor.compras()).isEqualTo(3);
        assertThat(valor.sinDatos()).isFalse();
    }

    @Test
    void conUnaSolaCompraEsElMontoDeEsaCompra() {
        ValorAcumulado valor = ValorAcumulado.de(new ResumenCompras(1, new BigDecimal("350.50")));

        assertThat(valor.valor()).isEqualByComparingTo("350.50");
        assertThat(valor.sinDatos()).isFalse();
    }

    @Test
    void sinComprasVigentesElValorEsCeroYNoNulo() {
        ValorAcumulado valor = ValorAcumulado.de(ResumenCompras.VACIO);

        assertThat(valor.valor()).isEqualByComparingTo("0");
        assertThat(valor.sinDatos()).isTrue();
        assertThat(valor.compras()).isZero();
    }
}
