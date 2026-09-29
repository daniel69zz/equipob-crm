package com.maxiconecta.crm.perfil.consentimiento;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;

import static com.maxiconecta.crm.perfil.consentimiento.AlcanceConsentimiento.COMUNICACIONES_COMERCIALES;
import static com.maxiconecta.crm.perfil.consentimiento.AlcanceConsentimiento.GESTION_CLIENTE;
import static com.maxiconecta.crm.perfil.consentimiento.AlcanceConsentimiento.SEGMENTACION;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-183 · El consentimiento autoriza solo las finalidades otorgadas y solo mientras está vigente.
 */
class ConsentimientoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 29);
    private static final OffsetDateTime OTORGAMIENTO = OffsetDateTime.parse("2026-09-29T10:00:00-04:00");

    @Test
    void autorizaSoloLosAlcancesOtorgados() {
        Consentimiento consentimiento = otorgado(Set.of(GESTION_CLIENTE, SEGMENTACION), HOY, null);

        assertThat(consentimiento.autoriza(GESTION_CLIENTE, HOY)).isTrue();
        assertThat(consentimiento.autoriza(SEGMENTACION, HOY)).isTrue();
        assertThat(consentimiento.autoriza(COMUNICACIONES_COMERCIALES, HOY)).isFalse();
    }

    @Test
    void fueraDeLaVigenciaNoAutorizaNada() {
        Consentimiento consentimiento = otorgado(Set.of(GESTION_CLIENTE), HOY, HOY.plusDays(30));

        assertThat(consentimiento.autoriza(GESTION_CLIENTE, HOY.minusDays(1))).isFalse();
        assertThat(consentimiento.autoriza(GESTION_CLIENTE, HOY.plusDays(30))).isTrue();
        assertThat(consentimiento.autoriza(GESTION_CLIENTE, HOY.plusDays(31))).isFalse();
    }

    @Test
    void revocadoNoAutorizaNadaYGuardaLaFecha() {
        Consentimiento consentimiento = otorgado(Set.of(GESTION_CLIENTE), HOY, null);

        consentimiento.revocar("Lo pidió por correo", "admin");

        assertThat(consentimiento.getEstado()).isEqualTo(EstadoConsentimiento.REVOCADO);
        assertThat(consentimiento.getFechaRevocacion()).isNotNull();
        assertThat(consentimiento.getMotivoRevocacion()).isEqualTo("Lo pidió por correo");
        assertThat(consentimiento.autoriza(GESTION_CLIENTE, HOY)).isFalse();
    }

    @Test
    void losMismosDatosNoSonUnCambio() {
        Consentimiento consentimiento = otorgado(Set.of(GESTION_CLIENTE), HOY, null);

        assertThat(consentimiento.aplicar(datos(Set.of(GESTION_CLIENTE), HOY, null), "otro")).isNull();
        assertThat(consentimiento.getActualizadoPor()).isEqualTo("admin");
    }

    @Test
    void ampliarElAlcanceEsUnaActualizacion() {
        Consentimiento consentimiento = otorgado(Set.of(GESTION_CLIENTE), HOY, null);

        assertThat(consentimiento.aplicar(datos(Set.of(GESTION_CLIENTE, SEGMENTACION), HOY, null), "otro"))
                .isEqualTo(OperacionConsentimiento.ACTUALIZACION);
        assertThat(consentimiento.getAlcances()).containsExactly(GESTION_CLIENTE, SEGMENTACION);
        assertThat(consentimiento.getActualizadoPor()).isEqualTo("otro");
    }

    @Test
    void otorgarDeNuevoDespuesDeRevocarLimpiaLaRevocacion() {
        Consentimiento consentimiento = otorgado(Set.of(GESTION_CLIENTE), HOY, null);
        consentimiento.revocar("motivo", "admin");

        assertThat(consentimiento.aplicar(datos(Set.of(GESTION_CLIENTE), HOY, null), "admin"))
                .isEqualTo(OperacionConsentimiento.OTORGAMIENTO);
        assertThat(consentimiento.getEstado()).isEqualTo(EstadoConsentimiento.OTORGADO);
        assertThat(consentimiento.getFechaRevocacion()).isNull();
        assertThat(consentimiento.getMotivoRevocacion()).isNull();
    }

    private static Consentimiento otorgado(Set<AlcanceConsentimiento> alcances, LocalDate desde, LocalDate hasta) {
        return Consentimiento.otorgar(1L, datos(alcances, desde, hasta), "admin");
    }

    private static DatosConsentimiento datos(Set<AlcanceConsentimiento> alcances, LocalDate desde, LocalDate hasta) {
        return new DatosConsentimiento(CanalConsentimiento.PRESENCIAL, alcances, OTORGAMIENTO, desde, hasta);
    }
}
