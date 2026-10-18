package com.maxiconecta.crm.segmentacion.simulacion;

import java.time.OffsetDateTime;
import java.util.List;

public record ResultadoSimulacion(long clientesEvaluados, long asignacionesSinMetricas,
        long clientesQueCambiarian, boolean reglaActiva, OffsetDateTime calculadoEn,
        OffsetDateTime metricasMasAntiguas, OffsetDateTime metricasMasRecientes,
        long duracionMs, List<Distribucion> distribucion) {
    public record Distribucion(String clave, String segmento, boolean propuesta,
                               long actual, long simulado, long diferencia) { }
}
