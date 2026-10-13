package com.maxiconecta.crm.comportamiento.evolucion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Un periodo de la evolución, recortado al rango consultado: {@code parcial} avisa que el rango
 * no lo cubre completo (docs/compra/evolucion-consumo-categoria.md#periodos).
 */
public record Periodo(String clave, LocalDate inicio, LocalDate fin, boolean parcial) {

    /** Todos los periodos que tocan {@code [desde, hasta]}, en orden, aunque no tengan compras. */
    public static List<Periodo> entre(LocalDate desde, LocalDate hasta, TipoPeriodo tipo) {
        List<Periodo> periodos = new ArrayList<>();
        for (LocalDate inicio = tipo.inicioDe(desde); !inicio.isAfter(hasta); inicio = tipo.siguiente(inicio)) {
            LocalDate fin = tipo.siguiente(inicio).minusDays(1);
            LocalDate inicioEnRango = inicio.isBefore(desde) ? desde : inicio;
            LocalDate finEnRango = fin.isAfter(hasta) ? hasta : fin;
            periodos.add(new Periodo(tipo.clave(inicio), inicioEnRango, finEnRango,
                    !inicioEnRango.equals(inicio) || !finEnRango.equals(fin)));
        }
        return periodos;
    }

    boolean contiene(LocalDate dia) {
        return !dia.isBefore(inicio) && !dia.isAfter(fin);
    }
}
