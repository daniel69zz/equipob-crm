package com.maxiconecta.crm.segmentacion.asignacion;

import com.maxiconecta.crm.segmentacion.criterio.Criterio;
import com.maxiconecta.crm.segmentacion.criterio.Criterio.Indicador;
import com.maxiconecta.crm.segmentacion.criterio.ReglasCriterio;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Elige un único segmento cuando el cliente cumple varios criterios activos (SCRUM-260,
 * docs/segmentacion/asignacion-automatica.md):
 * <ol>
 *   <li>valor, frecuencia, clasificación por frecuencia (SCRUM-38) y categoría, en ese orden;</li>
 *   <li>entre criterios de categoría, el de la categoría en la que más consumió el cliente;</li>
 *   <li>si aún empatan, el criterio más antiguo (menor id).</li>
 * </ol>
 * La pertenencia a cada criterio es la de SCRUM-25 ({@link ReglasCriterio#cumple}).
 */
public final class ReglaPrioridadSegmento {

    private static final List<Indicador> ORDEN_INDICADORES = List.of(Indicador.VALOR, Indicador.FRECUENCIA,
            Indicador.CLASIFICACION_FRECUENCIA, Indicador.CATEGORIA);

    private ReglaPrioridadSegmento() {
    }

    public static Optional<Criterio> elegir(List<Criterio> criterios, long frecuencia, BigDecimal valor,
                                            List<String> categoriasPorConsumo) {
        return elegir(criterios, frecuencia, valor, categoriasPorConsumo, null);
    }

    /** Igual que la anterior, con la clasificación por frecuencia del cliente, o {@code null} si no tiene. */
    public static Optional<Criterio> elegir(List<Criterio> criterios, long frecuencia, BigDecimal valor,
                                            List<String> categoriasPorConsumo, String clasificacion) {
        BigDecimal frecuenciaDecimal = BigDecimal.valueOf(frecuencia);
        return criterios.stream()
                .filter(c -> ReglasCriterio.cumple(c, frecuenciaDecimal, valor, categoriasPorConsumo, clasificacion))
                .min(Comparator.comparingInt((Criterio c) -> ORDEN_INDICADORES.indexOf(c.indicador()))
                        .thenComparingInt(c -> posicionDeCategoria(c, categoriasPorConsumo))
                        .thenComparingLong(Criterio::id));
    }

    /** Posición de la categoría del criterio en el consumo del cliente; 0 para criterios numéricos. */
    private static int posicionDeCategoria(Criterio criterio, List<String> categoriasPorConsumo) {
        if (criterio.indicador() != Indicador.CATEGORIA) {
            return 0;
        }
        String clave = ReglasCriterio.clave(criterio.categoria());
        for (int i = 0; i < categoriasPorConsumo.size(); i++) {
            if (ReglasCriterio.clave(categoriasPorConsumo.get(i)).equals(clave)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }
}
