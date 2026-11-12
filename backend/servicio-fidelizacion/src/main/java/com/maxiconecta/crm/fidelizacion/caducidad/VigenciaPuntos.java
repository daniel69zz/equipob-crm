package com.maxiconecta.crm.fidelizacion.caducidad;

import com.maxiconecta.crm.fidelizacion.parametro.CodigosParametros;
import com.maxiconecta.crm.fidelizacion.parametro.ParametrosGenerales;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.IntSupplier;

/**
 * Configuración de la vigencia de los puntos y del proceso diario de caducidad (SCRUM-416). La vigencia es el
 * parámetro general {@code PUNTOS_VIGENCIA_MESES} que configura el Administrador de CRM (SCRUM-517); su valor inicial
 * es {@code fidelizacion.puntos.vigencia-meses} ({@code FIDELIZACION_VIGENCIA_MESES}, 12 por defecto). Todo cálculo
 * de caducidad la lee de aquí en el momento, así que un cambio rige en la consulta y la caducidad siguientes.
 */
@Component
public class VigenciaPuntos {

    static final String REGLA = "El saldo completo caduca %d meses después de la última compra que acumuló puntos";

    private final IntSupplier meses;
    private final String cron;
    private final String zonaHoraria;

    @Autowired
    public VigenciaPuntos(ParametrosGenerales parametros,
                          @Value("${fidelizacion.caducidad.cron:0 0 2 * * *}") String cron,
                          @Value("${fidelizacion.caducidad.zona-horaria:America/La_Paz}") String zonaHoraria) {
        this(() -> parametros.entero(CodigosParametros.PUNTOS_VIGENCIA_MESES), cron, zonaHoraria);
    }

    /** Con una vigencia fija, para las pruebas. */
    public VigenciaPuntos(int meses, String cron, String zonaHoraria) {
        this(() -> meses, cron, zonaHoraria);
        if (meses < ReglaCaducidad.VIGENCIA_MINIMA || meses > ReglaCaducidad.VIGENCIA_MAXIMA) {
            throw new IllegalStateException("fidelizacion.puntos.vigencia-meses debe estar entre "
                    + ReglaCaducidad.VIGENCIA_MINIMA + " y " + ReglaCaducidad.VIGENCIA_MAXIMA + " meses");
        }
    }

    private VigenciaPuntos(IntSupplier meses, String cron, String zonaHoraria) {
        this.meses = meses;
        this.cron = cron;
        this.zonaHoraria = zonaHoraria;
    }

    public int meses() {
        return meses.getAsInt();
    }

    public Configuracion configuracion() {
        int vigentes = meses();
        return new Configuracion(vigentes, REGLA.formatted(vigentes), cron, zonaHoraria);
    }

    /** Lo que ve el Administrador de CRM: la vigencia, la regla y cuándo corre el proceso de caducidad. */
    public record Configuracion(int vigenciaMeses, String regla, String procesoCron, String zonaHoraria) {
    }
}
