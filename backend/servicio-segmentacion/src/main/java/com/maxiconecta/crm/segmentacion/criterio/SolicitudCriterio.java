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
        @PositiveOrZero Long version) { }
