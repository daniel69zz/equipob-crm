package com.maxiconecta.crm.comportamiento.cohorte;

import com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Matriz de retención por cohortes mensuales de primera compra (SCRUM-329, SCRUM-36,
 * docs/compra/retencion-cohortes.md). Valida el rango pedido, agrega el historial en la base
 * ({@link HistorialCohortes}) y calcula la retención ({@link CalculadoraRetencion}).
 */
@Service
public class AnalisisCohortes {

    public static final String GRANULARIDAD = "MENSUAL";
    public static final int MESES_POR_DEFECTO = 12;
    public static final int MESES_MAXIMOS = 36;

    private final HistorialCohortes historial;
    private final Clock reloj;
    private final ZoneId zona;

    public AnalisisCohortes(HistorialCohortes historial, Clock reloj,
                            @Value("${comportamiento.cohortes.zona:America/La_Paz}") ZoneId zona) {
        this.historial = historial;
        this.reloj = reloj;
        this.zona = zona;
    }

    /** {@code desde} y {@code hasta} son meses de cohorte (AAAA-MM); vacíos, los últimos 12 meses. */
    public MatrizRetencion analizar(String desde, String hasta) {
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        YearMonth mesActual = YearMonth.from(ahora.atZoneSameInstant(zona));
        YearMonth fin = vacio(hasta) ? mesActual : parsear(hasta, "hasta");
        YearMonth inicio = vacio(desde) ? fin.minusMonths(MESES_POR_DEFECTO - 1L) : parsear(desde, "desde");
        if (fin.isAfter(mesActual)) {
            throw new ReglaNegocioException("'hasta' (" + fin + ") no puede ser posterior al mes en curso (" + mesActual + ")");
        }
        if (inicio.isAfter(fin)) {
            throw new ReglaNegocioException("'desde' (" + inicio + ") no puede ser posterior a 'hasta' (" + fin + ")");
        }
        if (ChronoUnit.MONTHS.between(inicio, fin) + 1 > MESES_MAXIMOS) {
            throw new ReglaNegocioException("El rango no puede superar los " + MESES_MAXIMOS + " meses de cohortes");
        }
        List<ActividadCohorte> actividad = historial.actividad(inicio, fin, ahora, zona);
        return new MatrizRetencion(GRANULARIDAD, zona.getId(), inicio, fin, mesActual,
                CalculadoraRetencion.calcular(actividad, inicio, fin, mesActual));
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static YearMonth parsear(String texto, String parametro) {
        try {
            return YearMonth.parse(texto.trim());
        } catch (DateTimeParseException ex) {
            throw new ReglaNegocioException("'" + parametro + "' inválido: '" + texto + "' (se espera AAAA-MM)");
        }
    }

    public record MatrizRetencion(String granularidad, String zonaHoraria, YearMonth desde, YearMonth hasta,
                                  YearMonth mesActual, List<CalculadoraRetencion.CohorteRetencion> cohortes) {
    }
}
