package com.maxiconecta.crm.comportamiento.cambio;

import com.maxiconecta.crm.comportamiento.compra.ComprasPorPeriodoCliente;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Criterio para detectar variaciones relevantes en la frecuencia o el monto de compra (SCRUM-316,
 * docs/compra/cambios-comportamiento.md): compara el periodo actual (los últimos {@code ventanaDias})
 * con lo habitual del cliente, el promedio por periodo de los {@code periodosBase} periodos
 * anteriores. Una variación es relevante cuando su desvío relativo, en valor absoluto, alcanza el
 * {@code umbral}. Sin historial suficiente el cliente queda sin línea base y no se evalúa.
 */
@Component
public class CriterioCambioComportamiento {

    private static final long SEGUNDOS_POR_DIA = Duration.ofDays(1).toSeconds();

    private final int ventanaDias;
    private final int periodosBase;
    private final int minimoComprasBase;
    private final BigDecimal umbral;
    private final Clock reloj;

    public CriterioCambioComportamiento(
            @Value("${comportamiento.cambio-comportamiento.ventana-dias:90}") int ventanaDias,
            @Value("${comportamiento.cambio-comportamiento.periodos-base:3}") int periodosBase,
            @Value("${comportamiento.cambio-comportamiento.minimo-compras-base:3}") int minimoComprasBase,
            @Value("${comportamiento.cambio-comportamiento.umbral:0.5}") BigDecimal umbral,
            Clock reloj) {
        if (ventanaDias <= 0 || periodosBase <= 0 || minimoComprasBase <= 0) {
            throw new IllegalStateException("La ventana (" + ventanaDias + "), los periodos de la línea base ("
                    + periodosBase + ") y el mínimo de compras (" + minimoComprasBase + ") deben ser positivos");
        }
        if (umbral.signum() <= 0 || umbral.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("El umbral de variación (" + umbral.toPlainString()
                    + ") debe ser mayor que 0 y como máximo 1: una disminución nunca supera el 100 %");
        }
        this.ventanaDias = ventanaDias;
        this.periodosBase = periodosBase;
        this.minimoComprasBase = minimoComprasBase;
        this.umbral = umbral;
        this.reloj = reloj;
    }

    public int ventanaDias() {
        return ventanaDias;
    }

    public BigDecimal umbral() {
        return umbral;
    }

    /** Línea base y periodo actual de una evaluación hecha ahora. */
    public Ventanas ventanas() {
        OffsetDateTime fin = OffsetDateTime.now(reloj);
        OffsetDateTime inicioActual = fin.minusDays(ventanaDias);
        return new Ventanas(inicioActual.minusDays((long) ventanaDias * periodosBase), inicioActual, fin);
    }

    /**
     * Evalúa a un cliente: sin historial suficiente queda {@code SIN_LINEA_BASE}; si no, compara la
     * frecuencia y el monto del periodo actual con lo habitual, llevado a un periodo según los días de
     * línea base que se llegaron a observar (un cliente que empezó a comprar a mitad de la línea base
     * no tiene un habitual artificialmente bajo).
     */
    public Evaluacion evaluar(ComprasPorPeriodoCliente compras, Ventanas ventanas) {
        int comprasBase = compras.comprasBase().intValue();
        OffsetDateTime observadoDesde = compras.primeraCompra().isAfter(ventanas.inicioBase())
                ? compras.primeraCompra() : ventanas.inicioBase();
        long segundosObservados = Duration.between(observadoDesde, ventanas.inicioActual()).toSeconds();
        long segundosVentana = ventanaDias * SEGUNDOS_POR_DIA;
        if (comprasBase < minimoComprasBase || segundosObservados < segundosVentana) {
            return new Evaluacion(EstadoComportamiento.SIN_LINEA_BASE, comprasBase, List.of());
        }
        BigDecimal factor = BigDecimal.valueOf(segundosVentana)
                .divide(BigDecimal.valueOf(segundosObservados), 10, RoundingMode.HALF_UP);

        List<Variacion> variaciones = new ArrayList<>();
        variacion(TipoVariacion.FRECUENCIA, BigDecimal.valueOf(compras.comprasActual()),
                BigDecimal.valueOf(comprasBase).multiply(factor)).ifPresent(variaciones::add);
        return new Evaluacion(variaciones.isEmpty() ? EstadoComportamiento.ESTABLE : EstadoComportamiento.CON_VARIACION,
                comprasBase, List.copyOf(variaciones));
    }

    /** Variación relevante de un valor respecto a lo habitual; vacía si no alcanza el umbral. */
    Optional<Variacion> variacion(TipoVariacion tipo, BigDecimal actual, BigDecimal habitual) {
        BigDecimal habitualPorPeriodo = habitual.setScale(2, RoundingMode.HALF_UP);
        if (habitualPorPeriodo.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal relativa = actual.subtract(habitualPorPeriodo).divide(habitualPorPeriodo, 4, RoundingMode.HALF_UP);
        if (relativa.abs().compareTo(umbral) < 0) {
            return Optional.empty();
        }
        DireccionVariacion direccion = relativa.signum() > 0 ? DireccionVariacion.AUMENTO : DireccionVariacion.DISMINUCION;
        return Optional.of(new Variacion(tipo, direccion, actual.setScale(2, RoundingMode.HALF_UP), habitualPorPeriodo,
                relativa));
    }
}
