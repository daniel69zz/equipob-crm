package com.maxiconecta.crm.fidelizacion.movimiento;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SCRUM-538 · Regla de reversa: puntos de una anulación total o de una devolución parcial. */
class ReglaReversaTest {

    /** La compra de Ana: Bs 350.50 con 1 punto por cada Bs 10 → 35 puntos. */
    private static final ReglaReversa.Original COMPRA = new ReglaReversa.Original("V-100234", 42,
            new BigDecimal("350.50"), 35, new BigDecimal("10.00"), BigDecimal.ZERO, 1L);

    @Test
    void unaAnulacionTotalRevierteTodosLosPuntos() {
        assertThat(ReglaReversa.puntosARevertir(COMPRA, BigDecimal.ZERO, 0, "A1", new BigDecimal("350.50")))
                .isEqualTo(35);
    }

    @Test
    void unaDevolucionParcialRevierteSoloLosPuntosDelMontoDevuelto() {
        // Quedan Bs 300 vigentes → 30 puntos; se revierten 5.
        assertThat(ReglaReversa.puntosARevertir(COMPRA, BigDecimal.ZERO, 0, "D1", new BigDecimal("50.50")))
                .isEqualTo(5);
    }

    @Test
    void laAnulacionDelRestoRevierteLoQueQuedabaYLaSumaNoSuperaLosPuntosDeLaCompra() {
        long parcial = ReglaReversa.puntosARevertir(COMPRA, BigDecimal.ZERO, 0, "D1", new BigDecimal("50.50"));
        long resto = ReglaReversa.puntosARevertir(COMPRA, new BigDecimal("50.50"), parcial, "A1",
                new BigDecimal("300.00"));

        assertThat(resto).isEqualTo(30);
        assertThat(parcial + resto).isEqualTo(COMPRA.puntos());
    }

    @Test
    void variasDevolucionesPequenasNoPierdenNiDuplicanPuntosPorElRedondeo() {
        // Tres devoluciones de Bs 5: el redondeo hacia abajo se resuelve con lo que sigue vigente.
        long primera = ReglaReversa.puntosARevertir(COMPRA, BigDecimal.ZERO, 0, "D1", new BigDecimal("5.00"));
        long segunda = ReglaReversa.puntosARevertir(COMPRA, new BigDecimal("5.00"), primera, "D2", new BigDecimal("5.00"));
        long tercera = ReglaReversa.puntosARevertir(COMPRA, new BigDecimal("10.00"), primera + segunda, "D3",
                new BigDecimal("5.00"));

        assertThat(primera).isEqualTo(1);   // vigente 345.50 → 34
        assertThat(segunda).isZero();       // vigente 340.50 → 34
        assertThat(tercera).isEqualTo(1);   // vigente 335.50 → 33
    }

    @Test
    void siLoQueQuedaNoLlegaAlMontoMinimoSeRevierteTodo() {
        ReglaReversa.Original conMinimo = new ReglaReversa.Original("V-1", 42, new BigDecimal("120.00"), 12,
                new BigDecimal("10.00"), new BigDecimal("100.00"), 2L);

        assertThat(ReglaReversa.puntosARevertir(conMinimo, BigDecimal.ZERO, 0, "D1", new BigDecimal("30.00")))
                .isEqualTo(12);
    }

    @Test
    void unaReversaQueSuperaLoVigenteSeRechazaConLaCausa() {
        assertThatThrownBy(() -> ReglaReversa.puntosARevertir(COMPRA, new BigDecimal("300.00"), 30, "A2",
                new BigDecimal("100.00")))
                .isInstanceOf(EventoInvalidoException.class)
                .hasMessage("La reversa A2 (100.00) supera lo que sigue vigente de la transacción V-100234 (50.50)");
    }
}
