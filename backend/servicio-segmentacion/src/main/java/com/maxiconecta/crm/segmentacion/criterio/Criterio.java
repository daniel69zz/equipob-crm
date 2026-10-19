package com.maxiconecta.crm.segmentacion.criterio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Criterio de segmentación (SCRUM-25). {@code clasificacion} solo se usa con el indicador
 * {@link Indicador#CLASIFICACION_FRECUENCIA} (SCRUM-38): {@code OCASIONAL}, {@code HABITUAL} o
 * {@code FRECUENTE}.
 */
public record Criterio(Long id, String nombre, String descripcion, Indicador indicador, Condicion condicion,
                       BigDecimal minimo, BigDecimal maximo, String categoria, boolean activo, long version,
                       OffsetDateTime creadoEn, OffsetDateTime actualizadoEn, String actualizadoPor,
                       String clasificacion) {
    public enum Indicador { FRECUENCIA, VALOR, CATEGORIA, CLASIFICACION_FRECUENCIA }
    public enum Condicion { ENTRE, MAYOR_IGUAL, MENOR_IGUAL, IGUAL, CONTIENE }

    /** Clasificaciones por frecuencia que publica servicio-comportamiento (docs/compra/clasificacion-frecuencia.md). */
    public static final Set<String> CLASIFICACIONES = Set.of("OCASIONAL", "HABITUAL", "FRECUENTE");

    /** Criterio de los indicadores de SCRUM-25, sin clasificación. */
    public Criterio(Long id, String nombre, String descripcion, Indicador indicador, Condicion condicion,
                    BigDecimal minimo, BigDecimal maximo, String categoria, boolean activo, long version,
                    OffsetDateTime creadoEn, OffsetDateTime actualizadoEn, String actualizadoPor) {
        this(id, nombre, descripcion, indicador, condicion, minimo, maximo, categoria, activo, version, creadoEn,
                actualizadoEn, actualizadoPor, null);
    }
}
