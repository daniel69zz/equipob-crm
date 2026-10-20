package com.maxiconecta.crm.comportamiento.riesgo;

import com.maxiconecta.crm.comportamiento.cambio.CriterioCambioComportamiento;
import com.maxiconecta.crm.comportamiento.consumo.NivelConsumo;
import com.maxiconecta.crm.comportamiento.frecuencia.ClasificacionFrecuencia;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Riesgo de abandono de un cliente con los factores que lo explican (SCRUM-325, criterio de
 * aceptación 4 de SCRUM-35): el nivel y el puntaje de su último cálculo y, por cada factor, los
 * puntos que aportó, su máximo y una explicación legible. Un cliente sin riesgo calculado responde
 * "sin datos", no un error.
 */
@Service
public class ConsultaRiesgoAbandono {

    private final RiesgoAbandonoClienteRepository riesgos;
    private final CriterioRiesgoAbandono criterio;
    private final CriterioCambioComportamiento cambios;

    public ConsultaRiesgoAbandono(RiesgoAbandonoClienteRepository riesgos, CriterioRiesgoAbandono criterio,
                                  CriterioCambioComportamiento cambios) {
        this.riesgos = riesgos;
        this.criterio = criterio;
        this.cambios = cambios;
    }

    @Transactional(readOnly = true)
    public RiesgoAbandono buscar(long clienteId) {
        return riesgos.findById(clienteId).map(this::respuesta).orElseGet(() -> RiesgoAbandono.sinDatos(clienteId));
    }

    private RiesgoAbandono respuesta(RiesgoAbandonoCliente riesgo) {
        List<Factor> factores = List.of(
                new Factor(FactorRiesgo.RECENCIA, riesgo.getPuntosRecencia(), criterio.pesoRecencia(), recencia(riesgo)),
                new Factor(FactorRiesgo.FRECUENCIA, riesgo.getPuntosFrecuencia(), criterio.pesoFrecuencia(),
                        frecuencia(riesgo.getClasificacionFrecuencia(), riesgo.getCompras())),
                new Factor(FactorRiesgo.VALOR, riesgo.getPuntosValor(), criterio.pesoValor(),
                        valor(riesgo.getNivelConsumo(), riesgo.getValorAcumulado())),
                new Factor(FactorRiesgo.CAMBIO_COMPORTAMIENTO, riesgo.getPuntosCambio(), criterio.penalizacionCambio(),
                        cambio(riesgo.isCambioDetectado())));
        return new RiesgoAbandono(riesgo.getClienteId(), riesgo.getNivel(), riesgo.getPuntaje(), riesgo.isInactivo(),
                riesgo.getEnSeguimientoDesde(), riesgo.getCalculadoEn(), factores, false);
    }

    /** Ej.: "50 días sin comprar (se considera inactivo después de 90)". */
    String recencia(RiesgoAbandonoCliente riesgo) {
        int dias = riesgo.getDiasSinComprar();
        String sinComprar = dias == 1 ? "1 día sin comprar" : dias + " días sin comprar";
        int umbral = criterio.umbralInactividadDias();
        return riesgo.isInactivo() ? sinComprar + ": inactivo (más de " + umbral + " días)"
                : sinComprar + " (se considera inactivo después de " + umbral + ")";
    }

    /** Ej.: "Habitual: 5 compras vigentes". */
    static String frecuencia(ClasificacionFrecuencia clasificacion, long compras) {
        String nombre = switch (clasificacion) {
            case OCASIONAL -> "Ocasional";
            case HABITUAL -> "Habitual";
            case FRECUENTE -> "Frecuente";
        };
        return nombre + ": " + (compras == 1 ? "1 compra vigente" : compras + " compras vigentes");
    }

    /** Ej.: "Nivel de consumo medio: Bs 1500.00 acumulados". */
    static String valor(NivelConsumo nivel, BigDecimal valorAcumulado) {
        return "Nivel de consumo " + nivel.name().toLowerCase(Locale.ROOT) + ": Bs " + valorAcumulado.toPlainString()
                + " acumulados";
    }

    String cambio(boolean detectado) {
        return detectado
                ? "Bajó su frecuencia o su monto de compra en los últimos " + cambios.ventanaDias() + " días"
                : "Sin disminuciones detectadas en los últimos " + cambios.ventanaDias() + " días";
    }

    /** {@code nivel} es {@code null} y {@code sinDatos} verdadero cuando el cliente no tiene riesgo calculado. */
    public record RiesgoAbandono(Long clienteId, NivelRiesgo nivel, BigDecimal puntaje, boolean inactivo,
                                 OffsetDateTime enSeguimientoDesde, OffsetDateTime calculadoEn, List<Factor> factores,
                                 boolean sinDatos) {

        static RiesgoAbandono sinDatos(long clienteId) {
            return new RiesgoAbandono(clienteId, null, null, false, null, null, List.of(), true);
        }
    }

    public record Factor(FactorRiesgo factor, BigDecimal puntos, BigDecimal maximo, String descripcion) {
    }
}
