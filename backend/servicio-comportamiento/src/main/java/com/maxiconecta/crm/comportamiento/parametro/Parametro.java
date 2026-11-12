package com.maxiconecta.crm.comportamiento.parametro;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Un parámetro general con su valor vigente, su rango y quién lo cambió por última vez (SCRUM-65). */
public record Parametro(String codigo, String nombre, String descripcion, String unidad, BigDecimal valor,
                        BigDecimal minimo, BigDecimal maximo, int decimales, long version, String modificadoPor,
                        OffsetDateTime modificadoEn) {
}
