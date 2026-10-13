package com.maxiconecta.crm.segmentacion.asignacion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Métricas vigentes de un cliente publicadas por servicio-comportamiento tras una compra o una
 * anulación (docs/segmentacion/asignacion-automatica.md). Los campos desconocidos se ignoran para
 * que el emisor pueda agregar campos sin romper la segmentación.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MetricasCliente(String idEvento, String tipoEventoOrigen, Long idCliente,
                              List<String> identificadoresOrigen, Long frecuencia, BigDecimal valor,
                              List<String> categorias, OffsetDateTime calculadasEn) {

    static final int LARGO_MAXIMO_ID_EVENTO = 64;

    /** Motivo por el que el mensaje no se puede aplicar, o vacío si está completo. */
    String problema() {
        if (idEvento == null || idEvento.isBlank() || idEvento.length() > LARGO_MAXIMO_ID_EVENTO) {
            return "idEvento es obligatorio (hasta " + LARGO_MAXIMO_ID_EVENTO + " caracteres)";
        }
        if (idCliente == null || idCliente <= 0) {
            return "idCliente es obligatorio y positivo";
        }
        if (frecuencia == null || frecuencia < 0) {
            return "frecuencia es obligatoria y no negativa";
        }
        if (valor == null || valor.signum() < 0) {
            return "valor es obligatorio y no negativo";
        }
        if (calculadasEn == null) {
            return "calculadasEn es obligatorio";
        }
        return "";
    }

    List<String> categoriasOVacio() {
        return categorias == null ? List.of() : categorias.stream().filter(c -> c != null && !c.isBlank()).toList();
    }
}
