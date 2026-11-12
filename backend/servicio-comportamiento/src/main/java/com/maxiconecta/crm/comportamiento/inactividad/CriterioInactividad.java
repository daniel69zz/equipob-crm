package com.maxiconecta.crm.comportamiento.inactividad;

import com.maxiconecta.crm.comportamiento.parametro.CodigosParametros;
import com.maxiconecta.crm.comportamiento.parametro.ParametrosGenerales;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.function.IntSupplier;

/**
 * Criterio de cliente inactivo (SCRUM-295, docs/compra/clientes-inactivos.md): no registra
 * ninguna compra vigente (estado distinto de ANULADA) en los últimos {@code umbralDias} días.
 *
 * <p>El umbral es un parámetro general del CRM que configura el Administrador (SCRUM-515): se lee en cada cálculo,
 * así que un cambio rige en la consulta siguiente sin reiniciar el servicio.
 */
@Component
public class CriterioInactividad {

    private final IntSupplier umbralDias;
    private final Clock reloj;

    @Autowired
    public CriterioInactividad(ParametrosGenerales parametros, Clock reloj) {
        this(() -> parametros.entero(CodigosParametros.INACTIVIDAD_DIAS), reloj);
    }

    /** Con un umbral fijo, para las pruebas. */
    public CriterioInactividad(int umbralDias, Clock reloj) {
        this(() -> umbralDias, reloj);
    }

    private CriterioInactividad(IntSupplier umbralDias, Clock reloj) {
        this.umbralDias = umbralDias;
        this.reloj = reloj;
    }

    public int umbralDias() {
        return umbralDias.getAsInt();
    }

    /** Una última compra anterior a esta fecha hace al cliente inactivo. */
    public OffsetDateTime fechaCorte() {
        return OffsetDateTime.now(reloj).minusDays(umbralDias());
    }
}
