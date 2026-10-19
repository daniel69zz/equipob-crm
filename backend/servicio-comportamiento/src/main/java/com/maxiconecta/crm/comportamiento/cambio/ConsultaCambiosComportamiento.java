package com.maxiconecta.crm.comportamiento.cambio;

import com.maxiconecta.crm.comportamiento.compra.Identificador;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Alertas de cambio de comportamiento de un cliente (SCRUM-315, docs/compra/cambios-comportamiento.md):
 * el estado de su última evaluación y las detecciones registradas, con la información mínima para
 * interpretarlas (tipo, dirección, valores comparados, periodo y un mensaje legible).
 */
@Service
public class ConsultaCambiosComportamiento {

    public static final int MAXIMO_ALERTAS = 50;

    /** Ninguno de los identificadores del cliente se evaluó todavía; no es un error. */
    static final String SIN_EVALUAR = "SIN_EVALUAR";

    private final DeteccionCambioComportamientoRepository detecciones;
    private final EstadoComportamientoClienteRepository estados;
    private final CriterioCambioComportamiento criterio;

    public ConsultaCambiosComportamiento(DeteccionCambioComportamientoRepository detecciones,
                                         EstadoComportamientoClienteRepository estados,
                                         CriterioCambioComportamiento criterio) {
        this.detecciones = detecciones;
        this.estados = estados;
        this.criterio = criterio;
    }

    /**
     * Combina los identificadores del cliente: el estado más relevante entre ellos
     * ({@code CON_VARIACION}, luego {@code ESTABLE}, luego {@code SIN_LINEA_BASE}) o
     * {@code SIN_EVALUAR} si ninguno se evaluó todavía, y todas sus alertas juntas.
     */
    @Transactional(readOnly = true)
    public CambiosComportamiento buscar(List<Identificador> identificadores) {
        if (identificadores.isEmpty()) {
            return new CambiosComportamiento(SIN_EVALUAR, null, List.of());
        }
        List<String> ids = identificadores.stream().map(Identificador::idClienteOrigen).toList();
        List<EstadoComportamientoCliente> evaluados = estados.findAllById(ids);
        String estado = evaluados.stream().map(EstadoComportamientoCliente::getEstado)
                .min(Comparator.comparingInt(ConsultaCambiosComportamiento::prioridad))
                .map(Enum::name).orElse(SIN_EVALUAR);
        OffsetDateTime evaluadoEn = evaluados.stream().map(EstadoComportamientoCliente::getEvaluadoEn)
                .max(Comparator.naturalOrder()).orElse(null);
        List<Alerta> alertas = detecciones.findByIdClienteOrigenInOrderByDetectadoEnDescIdDesc(ids,
                PageRequest.of(0, MAXIMO_ALERTAS)).stream().map(this::alerta).toList();
        return new CambiosComportamiento(estado, evaluadoEn, alertas);
    }

    private static int prioridad(EstadoComportamiento estado) {
        return switch (estado) {
            case CON_VARIACION -> 0;
            case ESTABLE -> 1;
            case SIN_LINEA_BASE -> 2;
        };
    }

    private Alerta alerta(DeteccionCambioComportamiento deteccion) {
        return new Alerta(deteccion.getId(), deteccion.getIdClienteOrigen(), deteccion.getTipo(),
                deteccion.getDireccion(), deteccion.getValorActual(), deteccion.getValorHabitual(),
                deteccion.getVariacion(), deteccion.getUmbral(), deteccion.getPeriodoDesde(),
                deteccion.getPeriodoHasta(), deteccion.getDetectadoEn(), mensaje(deteccion));
    }

    /** Ej.: "La frecuencia de compra bajó un 75 % respecto a lo habitual (1 frente a 4 por periodo de 90 días)". */
    String mensaje(DeteccionCambioComportamiento deteccion) {
        String que = deteccion.getTipo() == TipoVariacion.FRECUENCIA ? "La frecuencia de compra" : "El monto de compra";
        String sentido = deteccion.getDireccion() == DireccionVariacion.AUMENTO ? "subió" : "bajó";
        BigDecimal porcentaje = deteccion.getVariacion().abs().movePointRight(2).setScale(0, RoundingMode.HALF_UP);
        return que + " " + sentido + " un " + porcentaje.toPlainString() + " % respecto a lo habitual ("
                + legible(deteccion.getValorActual()) + " frente a " + legible(deteccion.getValorHabitual())
                + " por periodo de " + criterio.ventanaDias() + " días)";
    }

    private static String legible(BigDecimal valor) {
        BigDecimal sinCeros = valor.stripTrailingZeros();
        return (sinCeros.scale() < 0 ? sinCeros.setScale(0) : sinCeros).toPlainString();
    }

    public record CambiosComportamiento(String estado, OffsetDateTime evaluadoEn, List<Alerta> alertas) {
    }

    public record Alerta(Long id, String idClienteOrigen, TipoVariacion tipo, DireccionVariacion direccion,
                         BigDecimal valorActual, BigDecimal valorHabitual, BigDecimal variacion, BigDecimal umbral,
                         OffsetDateTime periodoDesde, OffsetDateTime periodoHasta, OffsetDateTime detectadoEn,
                         String mensaje) {
    }
}
