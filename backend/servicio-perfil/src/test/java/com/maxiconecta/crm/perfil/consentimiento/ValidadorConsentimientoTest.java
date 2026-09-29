package com.maxiconecta.crm.perfil.consentimiento;

import com.maxiconecta.crm.perfil.comun.ReglaNegocioException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Set;

import static com.maxiconecta.crm.perfil.consentimiento.AlcanceConsentimiento.ANALISIS_COMPORTAMIENTO;
import static com.maxiconecta.crm.perfil.consentimiento.AlcanceConsentimiento.GESTION_CLIENTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCRUM-181 · Validaciones de fecha y consistencia del consentimiento.
 */
class ValidadorConsentimientoTest {

    private static final ZoneId LA_PAZ = ZoneId.of("America/La_Paz");
    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2026-09-29T10:00:00-04:00");
    private static final LocalDate HOY = LocalDate.of(2026, 9, 29);

    private final ValidadorConsentimiento validador = new ValidadorConsentimiento(LA_PAZ);

    @Test
    void sinFechasUsaHoyYElMomentoDelRegistroYNoVence() {
        DatosConsentimiento datos = validador.validar(solicitud(null, null, null), null, AHORA);

        assertThat(datos.vigenciaDesde()).isEqualTo(HOY);
        assertThat(datos.vigenciaHasta()).isNull();
        assertThat(datos.fechaOtorgamiento()).isEqualTo(AHORA);
        assertThat(datos.alcances()).containsExactlyInAnyOrder(GESTION_CLIENTE, ANALISIS_COMPORTAMIENTO);
    }

    @Test
    void aceptaUnaVigenciaDeUnSoloDia() {
        DatosConsentimiento datos = validador.validar(solicitud(HOY, HOY, null), null, AHORA);

        assertThat(datos.vigenciaHasta()).isEqualTo(HOY);
    }

    // --- Criterio 3 ---

    @Test
    void rechazaUnaVigenciaQueTerminaAntesDeEmpezar() {
        assertThatThrownBy(() -> validador.validar(solicitud(HOY.plusDays(10), HOY.plusDays(2), null), null, AHORA))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La fecha final de la vigencia (2026-10-01) no puede ser anterior a la inicial (2026-10-09)");
    }

    @Test
    void rechazaUnaVigenciaQueYaTermino() {
        assertThatThrownBy(() -> validador.validar(solicitud(HOY.minusDays(30), HOY.minusDays(1),
                AHORA.minusDays(30)), null, AHORA))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageStartingWith("La vigencia terminó el 2026-09-28");
    }

    @Test
    void rechazaUnOtorgamientoFuturo() {
        assertThatThrownBy(() -> validador.validar(solicitud(null, null, AHORA.plusMinutes(5)), null, AHORA))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La fecha de otorgamiento no puede ser futura");
    }

    @Test
    void rechazaUnaVigenciaQueEmpiezaAntesDelOtorgamiento() {
        assertThatThrownBy(() -> validador.validar(solicitud(HOY.minusDays(5), null, AHORA.minusDays(1)), null, AHORA))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La vigencia no puede empezar (2026-09-24) antes de la fecha de otorgamiento (2026-09-28)");
    }

    @Test
    void comparaElDiaDelOtorgamientoEnLaZonaDelCrm() {
        // 01:30 UTC del 29 es todavía 28 en La Paz: la vigencia puede empezar el 28.
        OffsetDateTime otorgamiento = OffsetDateTime.parse("2026-09-29T01:30:00Z");

        DatosConsentimiento datos = validador.validar(solicitud(HOY.minusDays(1), null, otorgamiento), null, AHORA);

        assertThat(datos.vigenciaDesde()).isEqualTo(HOY.minusDays(1));
    }

    @Test
    void exigeAlMenosUnAlcanceYUnCanal() {
        assertThatThrownBy(() -> validador.validar(new SolicitudConsentimiento(CanalConsentimiento.PRESENCIAL, Set.of(),
                null, null, null), null, AHORA))
                .hasMessage("Debe autorizar al menos un alcance");
        assertThatThrownBy(() -> validador.validar(new SolicitudConsentimiento(CanalConsentimiento.PRESENCIAL, null,
                null, null, null), null, AHORA))
                .hasMessage("Debe autorizar al menos un alcance");
        assertThatThrownBy(() -> validador.validar(new SolicitudConsentimiento(null, Set.of(GESTION_CLIENTE),
                null, null, null), null, AHORA))
                .hasMessage("Debe indicar el canal del consentimiento");
    }

    // --- Consistencia con el consentimiento ya registrado ---

    @Test
    void alActualizarUnoOtorgadoConservaLasFechasQueNoSeEnvian() {
        OffsetDateTime otorgadoAntes = AHORA.minusDays(10);
        Consentimiento actual = otorgado(otorgadoAntes, HOY.minusDays(10));

        DatosConsentimiento datos = validador.validar(solicitud(null, HOY.plusYears(1), null), actual, AHORA);

        assertThat(datos.fechaOtorgamiento()).isEqualTo(otorgadoAntes);
        assertThat(datos.vigenciaDesde()).isEqualTo(HOY.minusDays(10));
    }

    @Test
    void unNuevoOtorgamientoNoPuedeSerAnteriorALaRevocacion() {
        Consentimiento revocado = otorgado(AHORA.minusDays(10), HOY.minusDays(10));
        revocado.revocar(null, "admin");
        OffsetDateTime antesDeRevocar = revocado.getFechaRevocacion().minusHours(1);
        LocalDate diaAntesDeRevocar = antesDeRevocar.atZoneSameInstant(LA_PAZ).toLocalDate();

        assertThatThrownBy(() -> validador.validar(solicitud(diaAntesDeRevocar, null, antesDeRevocar), revocado,
                revocado.getFechaRevocacion().plusDays(1)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageStartingWith("El nuevo otorgamiento no puede ser anterior a la revocación del");
    }

    @Test
    void despuesDeUnaRevocacionLasFechasPorDefectoSonLasDeHoy() {
        Consentimiento revocado = otorgado(AHORA.minusDays(10), HOY.minusDays(10));
        revocado.revocar(null, "admin");
        OffsetDateTime despues = revocado.getFechaRevocacion().plusSeconds(1);

        DatosConsentimiento datos = validador.validar(solicitud(null, null, null), revocado, despues);

        assertThat(datos.fechaOtorgamiento()).isEqualTo(despues);
        assertThat(datos.vigenciaDesde()).isEqualTo(despues.atZoneSameInstant(LA_PAZ).toLocalDate());
    }

    private static SolicitudConsentimiento solicitud(LocalDate desde, LocalDate hasta, OffsetDateTime otorgamiento) {
        return new SolicitudConsentimiento(CanalConsentimiento.PRESENCIAL, Set.of(GESTION_CLIENTE, ANALISIS_COMPORTAMIENTO),
                desde, hasta, otorgamiento);
    }

    private static Consentimiento otorgado(OffsetDateTime otorgamiento, LocalDate desde) {
        return Consentimiento.otorgar(1L, new DatosConsentimiento(CanalConsentimiento.MARKETPLACE_VENTAS, Set.of(GESTION_CLIENTE),
                otorgamiento, desde, null), "admin");
    }
}
