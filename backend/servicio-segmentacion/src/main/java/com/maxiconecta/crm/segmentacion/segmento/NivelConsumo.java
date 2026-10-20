package com.maxiconecta.crm.segmentacion.segmento;

import java.util.Locale;
import java.util.Optional;

/**
 * Nivel de consumo del cliente (SCRUM-27, docs/compra/nivel-consumo.md). Lo calcula
 * servicio-comportamiento y llega con las métricas del cliente (SCRUM-277); Segmentación no lo
 * calcula, solo lo guarda para listar y filtrar clientes (SCRUM-28).
 */
public enum NivelConsumo {
    BAJO,
    MEDIO,
    ALTO;

    /** El nivel con ese nombre, sin importar mayúsculas ni espacios; vacío si no es uno de los tres. */
    public static Optional<NivelConsumo> de(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(texto.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
