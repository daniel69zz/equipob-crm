package com.maxiconecta.crm.perfil.consentimiento;

import com.maxiconecta.crm.perfil.comun.ReglaNegocioException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Validaciones de fecha y consistencia del consentimiento (SCRUM-181), con los mensajes de
 * docs/perfil/consentimiento-datos.md. Completa los campos opcionales de la solicitud y devuelve
 * los datos listos para aplicar, o lanza {@link ReglaNegocioException} con la regla incumplida.
 */
public class ValidadorConsentimiento {

    private final ZoneId zona;

    public ValidadorConsentimiento(ZoneId zona) {
        this.zona = zona;
    }

    /**
     * @param actual el consentimiento registrado del cliente, o null si todavía no tiene
     * @param ahora  momento del registro
     */
    public DatosConsentimiento validar(SolicitudConsentimiento solicitud, Consentimiento actual, OffsetDateTime ahora) {
        if (solicitud.canal() == null) {
            throw new ReglaNegocioException("Debe indicar el canal del consentimiento");
        }
        if (solicitud.alcances() == null || solicitud.alcances().isEmpty() || solicitud.alcances().stream().anyMatch(Objects::isNull)) {
            throw new ReglaNegocioException("Debe autorizar al menos un alcance");
        }
        boolean actualizaUnoOtorgado = actual != null && actual.getEstado() == EstadoConsentimiento.OTORGADO;
        LocalDate hoy = ahora.atZoneSameInstant(zona).toLocalDate();

        OffsetDateTime fechaOtorgamiento = solicitud.fechaOtorgamiento() != null ? solicitud.fechaOtorgamiento()
                : actualizaUnoOtorgado ? actual.getFechaOtorgamiento() : ahora;
        LocalDate vigenciaDesde = solicitud.vigenciaDesde() != null ? solicitud.vigenciaDesde()
                : actualizaUnoOtorgado ? actual.getVigenciaDesde() : hoy;
        LocalDate vigenciaHasta = solicitud.vigenciaHasta();

        if (fechaOtorgamiento.isAfter(ahora)) {
            throw new ReglaNegocioException("La fecha de otorgamiento no puede ser futura");
        }
        if (vigenciaHasta != null && vigenciaHasta.isBefore(vigenciaDesde)) {
            throw new ReglaNegocioException("La fecha final de la vigencia (" + vigenciaHasta
                    + ") no puede ser anterior a la inicial (" + vigenciaDesde + ")");
        }
        if (vigenciaHasta != null && vigenciaHasta.isBefore(hoy)) {
            throw new ReglaNegocioException("La vigencia terminó el " + vigenciaHasta
                    + ": registre una vigencia que incluya la fecha de hoy o una futura");
        }
        LocalDate diaOtorgamiento = fechaOtorgamiento.atZoneSameInstant(zona).toLocalDate();
        if (vigenciaDesde.isBefore(diaOtorgamiento)) {
            throw new ReglaNegocioException("La vigencia no puede empezar (" + vigenciaDesde
                    + ") antes de la fecha de otorgamiento (" + diaOtorgamiento + ")");
        }
        if (actual != null && actual.getEstado() == EstadoConsentimiento.REVOCADO
                && fechaOtorgamiento.isBefore(actual.getFechaRevocacion())) {
            throw new ReglaNegocioException("El nuevo otorgamiento no puede ser anterior a la revocación del "
                    + actual.getFechaRevocacion().atZoneSameInstant(zona).toLocalDate());
        }
        return new DatosConsentimiento(solicitud.canal(), solicitud.alcances(), fechaOtorgamiento, vigenciaDesde,
                vigenciaHasta);
    }
}
