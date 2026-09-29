package com.maxiconecta.crm.comportamiento.indicador;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** SCRUM-19 · Cálculo exacto de la recencia y sus casos límite. */
class RecenciaCompraTest {

    @Test
    void calculaElTiempoExactoDesdeLaUltimaCompra() {
        OffsetDateTime compra = OffsetDateTime.parse("2026-09-27T15:28:10-04:00");

        RecenciaCompra recencia = RecenciaCompra.calcular(Optional.of(compra),
                Instant.parse("2026-09-29T20:58:10Z"));

        assertThat(recencia.ultimaCompra()).isEqualTo(compra);
        assertThat(recencia.tiempoTranscurrido()).isEqualTo(Duration.ofHours(49).plusMinutes(30));
        assertThat(recencia.sinDatos()).isFalse();
    }

    @Test
    void unaCompraEnElInstanteDeReferenciaTieneRecenciaCero() {
        OffsetDateTime compra = OffsetDateTime.parse("2026-09-29T16:00:00-04:00");

        RecenciaCompra recencia = RecenciaCompra.calcular(Optional.of(compra),
                Instant.parse("2026-09-29T20:00:00Z"));

        assertThat(recencia.tiempoTranscurrido()).isZero();
    }

    @Test
    void comparaInstantesYNoLasRepresentacionesDeZonaHoraria() {
        OffsetDateTime compra = OffsetDateTime.parse("2026-09-29T16:00:00-04:00");

        RecenciaCompra recencia = RecenciaCompra.calcular(Optional.of(compra),
                Instant.parse("2026-09-30T20:00:00Z"));

        assertThat(recencia.tiempoTranscurrido()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void unaFechaFuturaConservaLaDiferenciaExactaSinInventarUnaCorreccion() {
        OffsetDateTime compraFutura = OffsetDateTime.parse("2026-09-30T01:00:00Z");

        RecenciaCompra recencia = RecenciaCompra.calcular(Optional.of(compraFutura),
                Instant.parse("2026-09-30T00:00:00Z"));

        assertThat(recencia.tiempoTranscurrido()).isEqualTo(Duration.ofHours(-1));
    }

    @Test
    void sinComprasNoHayFechaNiTiempoTranscurrido() {
        RecenciaCompra recencia = RecenciaCompra.calcular(Optional.empty(), Instant.parse("2026-09-29T20:00:00Z"));

        assertThat(recencia.ultimaCompra()).isNull();
        assertThat(recencia.tiempoTranscurrido()).isNull();
        assertThat(recencia.sinDatos()).isTrue();
    }
}
