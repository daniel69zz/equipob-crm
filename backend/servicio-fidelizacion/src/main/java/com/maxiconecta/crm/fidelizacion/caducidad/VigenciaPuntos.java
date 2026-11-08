package com.maxiconecta.crm.fidelizacion.caducidad;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuración de la vigencia de los puntos y del proceso diario de caducidad (SCRUM-416). La vigencia sale de
 * {@code fidelizacion.puntos.vigencia-meses} ({@code FIDELIZACION_VIGENCIA_MESES}, 12 por defecto) hasta que
 * pase a los parámetros generales del CRM (SCRUM-65). Todo cálculo de caducidad la lee de aquí.
 */
@Component
public class VigenciaPuntos {

    static final String REGLA = "El saldo completo caduca %d meses después de la última compra que acumuló puntos";

    private final int meses;
    private final String cron;
    private final String zonaHoraria;

    public VigenciaPuntos(@Value("${fidelizacion.puntos.vigencia-meses:12}") int meses,
                          @Value("${fidelizacion.caducidad.cron:0 0 2 * * *}") String cron,
                          @Value("${fidelizacion.caducidad.zona-horaria:America/La_Paz}") String zonaHoraria) {
        if (meses < ReglaCaducidad.VIGENCIA_MINIMA || meses > ReglaCaducidad.VIGENCIA_MAXIMA) {
            throw new IllegalStateException("fidelizacion.puntos.vigencia-meses debe estar entre "
                    + ReglaCaducidad.VIGENCIA_MINIMA + " y " + ReglaCaducidad.VIGENCIA_MAXIMA + " meses");
        }
        this.meses = meses;
        this.cron = cron;
        this.zonaHoraria = zonaHoraria;
    }

    public int meses() {
        return meses;
    }

    public Configuracion configuracion() {
        return new Configuracion(meses, REGLA.formatted(meses), cron, zonaHoraria);
    }

    /** Lo que ve el Administrador de CRM: la vigencia, la regla y cuándo corre el proceso de caducidad. */
    public record Configuracion(int vigenciaMeses, String regla, String procesoCron, String zonaHoraria) {
    }
}
