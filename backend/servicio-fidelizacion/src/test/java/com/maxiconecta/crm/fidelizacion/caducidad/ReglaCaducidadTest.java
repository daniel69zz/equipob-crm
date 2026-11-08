package com.maxiconecta.crm.fidelizacion.caducidad;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCRUM-420 · Regla de caducidad de SCRUM-418 (docs/fidelizacion/caducidad-puntos.md).
 */
class ReglaCaducidadTest {

    @Test
    void elSaldoCaducaLosMesesDeVigenciaDespuesDeLaUltimaCompraEnUtc() {
        OffsetDateTime compra = OffsetDateTime.parse("2026-10-18T10:14:50-04:00");

        assertThat(ReglaCaducidad.fechaCaducidad(compra, 12)).isEqualTo(OffsetDateTime.parse("2027-10-18T14:14:50Z"));
    }

    @Test
    void siElDiaNoExisteEnElMesFinalCaducaElUltimoDiaDeEseMes() {
        assertThat(ReglaCaducidad.fechaCaducidad(OffsetDateTime.parse("2027-01-31T12:00:00Z"), 1))
                .isEqualTo(OffsetDateTime.parse("2027-02-28T12:00:00Z"));
        assertThat(ReglaCaducidad.fechaCaducidad(OffsetDateTime.parse("2027-01-31T12:00:00Z"), 13))
                .isEqualTo(OffsetDateTime.parse("2028-02-29T12:00:00Z"));
    }

    @Test
    void estaVencidaDesdeElMismoInstanteDeSuFechaDeCaducidad() {
        OffsetDateTime caducidad = OffsetDateTime.parse("2027-10-18T14:14:50Z");

        assertThat(ReglaCaducidad.vencida(caducidad, caducidad.minusSeconds(1))).isFalse();
        assertThat(ReglaCaducidad.vencida(caducidad, caducidad)).isTrue();
        assertThat(ReglaCaducidad.vencida(caducidad, OffsetDateTime.parse("2027-10-18T10:14:51-04:00"))).isTrue();
    }

    @Test
    void elMovimientoDependeSoloDelClienteYDelVencimiento() {
        OffsetDateTime caducidad = OffsetDateTime.parse("2027-10-18T10:14:50-04:00");

        assertThat(ReglaCaducidad.idMovimiento(42, caducidad)).isEqualTo("CADUCIDAD-42-20271018T141450Z");
        assertThat(ReglaCaducidad.idMovimiento(42, caducidad.withOffsetSameInstant(java.time.ZoneOffset.UTC)))
                .isEqualTo(ReglaCaducidad.idMovimiento(42, caducidad));
        assertThat(ReglaCaducidad.idMovimiento(42, caducidad.plusMonths(1))).isNotEqualTo(ReglaCaducidad.idMovimiento(42, caducidad));
    }

    @Test
    void laVigenciaConfiguradaDebeEstarEntre1Y120Meses() {
        assertThat(new VigenciaPuntos(1, "0 0 2 * * *", "America/La_Paz").meses()).isEqualTo(1);
        assertThat(new VigenciaPuntos(120, "0 0 2 * * *", "America/La_Paz").configuracion().regla())
                .isEqualTo("El saldo completo caduca 120 meses después de la última compra que acumuló puntos");
        assertThatThrownBy(() -> new VigenciaPuntos(0, "0 0 2 * * *", "America/La_Paz"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("entre 1 y 120");
        assertThatThrownBy(() -> new VigenciaPuntos(121, "0 0 2 * * *", "America/La_Paz"))
                .isInstanceOf(IllegalStateException.class);
    }
}
