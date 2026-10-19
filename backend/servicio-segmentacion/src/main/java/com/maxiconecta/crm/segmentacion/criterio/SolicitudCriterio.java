package com.maxiconecta.crm.segmentacion.criterio;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import static com.maxiconecta.crm.segmentacion.criterio.Criterio.*;

public record SolicitudCriterio(
        @NotBlank(message = "Indique el nombre") @Size(max = 100) String nombre,
        @Size(max = 500) String descripcion,
        @NotNull(message = "Seleccione un indicador") Indicador indicador,
        @NotNull(message = "Seleccione una condición") Condicion condicion,
        @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal minimo,
        @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal maximo,
        @Size(max = 100) String categoria,
        @PositiveOrZero Long version,
        @Size(max = 20) String clasificacion) {

    /** Solicitud de los indicadores de SCRUM-25, sin clasificación por frecuencia. */
    public SolicitudCriterio(String nombre, String descripcion, Indicador indicador, Condicion condicion,
                             BigDecimal minimo, BigDecimal maximo, String categoria, Long version) {
        this(nombre, descripcion, indicador, condicion, minimo, maximo, categoria, version, null);
    }
}
