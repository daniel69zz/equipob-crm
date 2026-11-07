package com.maxiconecta.crm.fidelizacion.nivel;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Una versión de la configuración de niveles (SCRUM-49, docs/fidelizacion/niveles-fidelizacion.md): el
 * periodo de evaluación en meses y el umbral de cada nivel, ordenados del más bajo al más alto.
 */
public record ConfiguracionNiveles(long version, int periodoMeses, List<UmbralNivel> umbrales, String motivo,
                                   String modificadoPor, OffsetDateTime vigenteDesde) {

    public ConfiguracionNiveles {
        umbrales = List.copyOf(umbrales);
    }

    /** Nivel que corresponde a los puntos del periodo; vacío si no alcanzan el umbral del nivel más bajo. */
    public Optional<Nivel> nivelPara(long puntosPeriodo) {
        return umbrales.stream().filter(u -> u.incluye(puntosPeriodo)).map(UmbralNivel::nivel).findFirst();
    }
}
