package com.maxiconecta.crm.segmentacion.criterio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record Criterio(Long id, String nombre, String descripcion, Indicador indicador, Condicion condicion,
                       BigDecimal minimo, BigDecimal maximo, String categoria, boolean activo, long version,
                       OffsetDateTime creadoEn, OffsetDateTime actualizadoEn, String actualizadoPor) {
    public enum Indicador { FRECUENCIA, VALOR, CATEGORIA }
    public enum Condicion { ENTRE, MAYOR_IGUAL, MENOR_IGUAL, IGUAL, CONTIENE }
}
