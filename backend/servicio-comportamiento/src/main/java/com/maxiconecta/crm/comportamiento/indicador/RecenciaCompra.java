package com.maxiconecta.crm.comportamiento.indicador;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Tiempo exacto transcurrido desde la última compra vigente confirmada del cliente. La duración
 * se expone en formato ISO-8601 para no imponer una unidad ni perder precisión por redondeo.
 */
public record RecenciaCompra(@JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime ultimaCompra,
                             @JsonFormat(shape = JsonFormat.Shape.STRING) Duration tiempoTranscurrido,
                             boolean sinDatos) {

    public static RecenciaCompra calcular(Optional<OffsetDateTime> ultimaCompra, Instant referencia) {
        return ultimaCompra
                .map(fecha -> new RecenciaCompra(fecha, Duration.between(fecha.toInstant(), referencia), false))
                .orElseGet(() -> new RecenciaCompra(null, null, true));
    }
}
