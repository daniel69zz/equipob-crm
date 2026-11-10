package com.maxiconecta.crm.fidelizacion.reporte;

import com.maxiconecta.crm.fidelizacion.nivel.Nivel;
import com.maxiconecta.crm.fidelizacion.puntos.SaldoPuntosRepository.ConteoNivel;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Clientes por nivel de fidelización (SCRUM-464). {@code total} es cuántos clientes se contaron, es
 * decir los que tienen saldo de puntos dentro del grupo pedido; quien no tiene puntos no aparece
 * aquí y lo cuenta el reporte como "sin nivel" (SCRUM-57).
 */
public record DistribucionNiveles(long total, List<Conteo> niveles) {

    /** Del nivel más bajo al más alto, y el nivel sin asignar al final. */
    static final Comparator<ConteoNivel> POR_NIVEL = Comparator.comparingInt(conteo -> orden(conteo.getNivel()));

    private static int orden(String nivel) {
        if (nivel == null || nivel.isBlank()) {
            return Nivel.values().length;
        }
        try {
            return Nivel.valueOf(nivel.strip().toUpperCase(Locale.ROOT)).ordinal();
        } catch (IllegalArgumentException ex) {
            // Un nivel que ya no esté en el catálogo se muestra igual, después de los conocidos.
            return Nivel.values().length + 1;
        }
    }

    /** {@code nivel} nulo es el cliente con puntos que todavía no alcanza el umbral más bajo. */
    public record Conteo(String nivel, long clientes) {
    }
}
