package com.maxiconecta.crm.comportamiento.indicador;

import com.maxiconecta.crm.comportamiento.compra.ResumenCompras;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-205 · Cálculo del ticket promedio con sus casos límite (docs/compra/ticket-promedio.md).
 */
class TicketPromedioTest {

    @Test
    void esLaSumaDeLosMontosEntreLaCantidadDeCompras() {
        TicketPromedio ticket = TicketPromedio.de(new ResumenCompras(3, new BigDecimal("600.00")));

        assertThat(ticket.valor()).isEqualByComparingTo("200.00");
        assertThat(ticket.compras()).isEqualTo(3);
        assertThat(ticket.montoAcumulado()).isEqualByComparingTo("600.00");
        assertThat(ticket.sinDatos()).isFalse();
    }

    @Test
    void conUnaSolaCompraEsElMontoDeEsaCompra() {
        TicketPromedio ticket = TicketPromedio.de(new ResumenCompras(1, new BigDecimal("350.50")));

        assertThat(ticket.valor()).isEqualByComparingTo("350.50");
        assertThat(ticket.sinDatos()).isFalse();
    }

    @Test
    void sinComprasNoHayTicketYNoSeDivideEntreCero() {
        TicketPromedio ticket = TicketPromedio.de(ResumenCompras.VACIO);

        assertThat(ticket.valor()).isNull();
        assertThat(ticket.sinDatos()).isTrue();
        assertThat(ticket.compras()).isZero();
        assertThat(ticket.montoAcumulado()).isEqualByComparingTo("0");
    }

    @Test
    void conMontoCeroPeroSinComprasSigueSiendoSinDatos() {
        TicketPromedio ticket = TicketPromedio.de(new ResumenCompras(0, BigDecimal.ZERO));

        assertThat(ticket.sinDatos()).isTrue();
        assertThat(ticket.valor()).isNull();
    }

    @Test
    void redondeaADosDecimalesHaciaArribaEnLaMitad() {
        // 100.00 / 3 = 33.333... y 0.05 / 2 = 0.025 (la mitad sube)
        assertThat(TicketPromedio.de(new ResumenCompras(3, new BigDecimal("100.00"))).valor())
                .isEqualByComparingTo("33.33");
        assertThat(TicketPromedio.de(new ResumenCompras(2, new BigDecimal("0.05"))).valor())
                .isEqualByComparingTo("0.03");
    }

    @Test
    void unTicketConDecimalesExactosConservaSuValor() {
        assertThat(TicketPromedio.de(new ResumenCompras(2, new BigDecimal("480.50"))).valor())
                .isEqualByComparingTo("240.25");
    }
}
