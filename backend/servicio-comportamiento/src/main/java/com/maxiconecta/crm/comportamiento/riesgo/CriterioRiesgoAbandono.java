package com.maxiconecta.crm.comportamiento.riesgo;

import com.maxiconecta.crm.comportamiento.consumo.CriterioNivelConsumo;
import com.maxiconecta.crm.comportamiento.consumo.NivelConsumo;
import com.maxiconecta.crm.comportamiento.frecuencia.ClasificacionFrecuencia;
import com.maxiconecta.crm.comportamiento.frecuencia.CriterioClasificacionFrecuencia;
import com.maxiconecta.crm.comportamiento.inactividad.CriterioInactividad;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Fórmula del riesgo de abandono (SCRUM-324, docs/compra/riesgo-abandono.md). Suma los puntos de
 * cuatro factores hasta 100:
 * <ul>
 *   <li>recencia: {@code pesoRecencia × min(días sin comprar / umbral de inactividad, 1)};</li>
 *   <li>frecuencia: todo el peso si es ocasional, la mitad si es habitual y nada si es frecuente
 *   (SCRUM-38);</li>
 *   <li>valor: todo el peso si su nivel de consumo es bajo, la mitad si es medio y nada si es alto
 *   (SCRUM-27);</li>
 *   <li>cambio de comportamiento: {@code penalizacionCambio} si se detectó una disminución (SCRUM-34).</li>
 * </ul>
 * Desde {@code umbralAlto} el riesgo es ALTO, desde {@code umbralMedio} MEDIO y por debajo BAJO; un
 * cliente inactivo (SCRUM-31) es siempre ALTO. Reutiliza los criterios de esas historias en vez de
 * definir cortes propios.
 */
@Component
public class CriterioRiesgoAbandono {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final BigDecimal MITAD = new BigDecimal("0.5");

    private final BigDecimal pesoRecencia;
    private final BigDecimal pesoFrecuencia;
    private final BigDecimal pesoValor;
    private final BigDecimal penalizacionCambio;
    private final BigDecimal umbralMedio;
    private final BigDecimal umbralAlto;
    private final CriterioInactividad inactividad;
    private final CriterioClasificacionFrecuencia frecuencia;
    private final CriterioNivelConsumo nivelConsumo;

    public CriterioRiesgoAbandono(
            @Value("${comportamiento.riesgo-abandono.peso-recencia:50}") BigDecimal pesoRecencia,
            @Value("${comportamiento.riesgo-abandono.peso-frecuencia:25}") BigDecimal pesoFrecuencia,
            @Value("${comportamiento.riesgo-abandono.peso-valor:25}") BigDecimal pesoValor,
            @Value("${comportamiento.riesgo-abandono.penalizacion-cambio:15}") BigDecimal penalizacionCambio,
            @Value("${comportamiento.riesgo-abandono.umbral-medio:35}") BigDecimal umbralMedio,
            @Value("${comportamiento.riesgo-abandono.umbral-alto:65}") BigDecimal umbralAlto,
            CriterioInactividad inactividad, CriterioClasificacionFrecuencia frecuencia,
            CriterioNivelConsumo nivelConsumo) {
        if (pesoRecencia.signum() < 0 || pesoFrecuencia.signum() < 0 || pesoValor.signum() < 0
                || pesoRecencia.add(pesoFrecuencia).add(pesoValor).compareTo(CIEN) != 0) {
            throw new IllegalStateException("Los pesos del riesgo de abandono no son válidos: recencia ("
                    + pesoRecencia.toPlainString() + "), frecuencia (" + pesoFrecuencia.toPlainString() + ") y valor ("
                    + pesoValor.toPlainString() + ") no pueden ser negativos y deben sumar 100");
        }
        if (penalizacionCambio.signum() < 0 || penalizacionCambio.compareTo(CIEN) > 0) {
            throw new IllegalStateException("La penalización por cambio de comportamiento ("
                    + penalizacionCambio.toPlainString() + ") debe estar entre 0 y 100");
        }
        if (umbralMedio.signum() <= 0 || umbralAlto.compareTo(umbralMedio) <= 0 || umbralAlto.compareTo(CIEN) > 0) {
            throw new IllegalStateException("Los umbrales del riesgo de abandono no son válidos: el medio ("
                    + umbralMedio.toPlainString() + ") debe ser mayor que cero y menor que el alto ("
                    + umbralAlto.toPlainString() + "), y el alto como máximo 100");
        }
        if (inactividad.umbralDias() <= 0) {
            throw new IllegalStateException("El umbral de inactividad (" + inactividad.umbralDias()
                    + " días) debe ser positivo para medir la recencia del riesgo de abandono");
        }
        this.pesoRecencia = pesoRecencia;
        this.pesoFrecuencia = pesoFrecuencia;
        this.pesoValor = pesoValor;
        this.penalizacionCambio = penalizacionCambio;
        this.umbralMedio = umbralMedio;
        this.umbralAlto = umbralAlto;
        this.inactividad = inactividad;
        this.frecuencia = frecuencia;
        this.nivelConsumo = nivelConsumo;
    }

    /**
     * Aplica la fórmula a un cliente con compras vigentes, en el instante {@code ahora}. Una compra
     * con fecha posterior a {@code ahora} cuenta como de hoy.
     */
    public EvaluacionRiesgo evaluar(DatosRiesgo datos, OffsetDateTime ahora) {
        if (datos.compras() <= 0 || datos.ultimaCompra() == null) {
            throw new IllegalArgumentException("El riesgo de abandono solo se calcula con compras vigentes");
        }
        int umbralDias = inactividad.umbralDias();
        long dias = Math.max(0, ChronoUnit.DAYS.between(datos.ultimaCompra(), ahora));
        boolean inactivo = datos.ultimaCompra().isBefore(ahora.minusDays(umbralDias));
        ClasificacionFrecuencia clasificacion = frecuencia.clasificar(datos.compras()).orElseThrow();
        NivelConsumo nivel = nivelConsumo.clasificar(datos.valorAcumulado()).orElse(NivelConsumo.BAJO);

        BigDecimal recencia = pesoRecencia.multiply(BigDecimal.valueOf(Math.min(dias, umbralDias)))
                .divide(BigDecimal.valueOf(umbralDias), 2, RoundingMode.HALF_UP);
        BigDecimal puntosFrecuencia = redondear(switch (clasificacion) {
            case OCASIONAL -> pesoFrecuencia;
            case HABITUAL -> pesoFrecuencia.multiply(MITAD);
            case FRECUENTE -> BigDecimal.ZERO;
        });
        BigDecimal puntosValor = redondear(switch (nivel) {
            case BAJO -> pesoValor;
            case MEDIO -> pesoValor.multiply(MITAD);
            case ALTO -> BigDecimal.ZERO;
        });
        BigDecimal cambio = redondear(datos.disminucionDetectada() ? penalizacionCambio : BigDecimal.ZERO);
        BigDecimal puntaje = recencia.add(puntosFrecuencia).add(puntosValor).add(cambio).min(CIEN);

        return new EvaluacionRiesgo(redondear(puntaje), nivel(puntaje, inactivo), dias, datos.ultimaCompra(),
                datos.compras(), datos.valorAcumulado(), clasificacion, nivel, inactivo, datos.disminucionDetectada(),
                recencia, puntosFrecuencia, puntosValor, cambio);
    }

    /** Un puntaje exactamente en un umbral pertenece al nivel superior; un cliente inactivo es siempre ALTO. */
    NivelRiesgo nivel(BigDecimal puntaje, boolean inactivo) {
        if (inactivo || puntaje.compareTo(umbralAlto) >= 0) {
            return NivelRiesgo.ALTO;
        }
        return puntaje.compareTo(umbralMedio) >= 0 ? NivelRiesgo.MEDIO : NivelRiesgo.BAJO;
    }

    public BigDecimal pesoRecencia() {
        return pesoRecencia;
    }

    public BigDecimal pesoFrecuencia() {
        return pesoFrecuencia;
    }

    public BigDecimal pesoValor() {
        return pesoValor;
    }

    public BigDecimal penalizacionCambio() {
        return penalizacionCambio;
    }

    public BigDecimal umbralMedio() {
        return umbralMedio;
    }

    public BigDecimal umbralAlto() {
        return umbralAlto;
    }

    public int umbralInactividadDias() {
        return inactividad.umbralDias();
    }

    private static BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
