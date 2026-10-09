package com.maxiconecta.crm.comportamiento.evolucion;

import com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Rango de fechas, tipo de periodo, categorías y periodo base de la evolución del consumo
 * (SCRUM-302, docs/compra/evolucion-consumo-categoria.md#filtros-scrum-302). Sin fechas, el rango
 * son los últimos 12 meses; sin categorías, se muestran todas; sin periodo base, se compara con el
 * primer periodo del rango.
 */
public record FiltroEvolucionConsumo(LocalDate desde, LocalDate hasta, TipoPeriodo periodo,
                                     Set<String> categorias, List<Periodo> periodos, String periodoBase) {

    static final int MESES_POR_DEFECTO = 12;
    static final int MAXIMO_PERIODOS = 60;
    static final int MAXIMO_CATEGORIAS = 50;
    static final int LARGO_MAXIMO_CATEGORIA = 100;

    /**
     * Valida los parámetros de la consulta y completa los que faltan.
     *
     * @param hoy fecha actual en la zona horaria del negocio, para el rango por defecto
     */
    public static FiltroEvolucionConsumo crear(LocalDate desde, LocalDate hasta, String periodo,
                                               List<String> categorias, String periodoBase, LocalDate hoy) {
        if (hasta == null) {
            hasta = desde != null && desde.isAfter(hoy) ? desde : hoy;
        }
        if (desde == null) {
            desde = hasta.withDayOfMonth(1).minusMonths(MESES_POR_DEFECTO - 1);
        }
        if (desde.isAfter(hasta)) {
            throw new ReglaNegocioException("La fecha 'desde' no puede ser posterior a 'hasta'");
        }
        TipoPeriodo tipo = TipoPeriodo.parsear(periodo);
        long cantidad = tipo.cantidadEntre(desde, hasta);
        if (cantidad > MAXIMO_PERIODOS) {
            throw new ReglaNegocioException("El rango abarca " + cantidad + " periodos " + tipo.name().toLowerCase()
                    + "es; el máximo es " + MAXIMO_PERIODOS + ". Acorte el rango o elija un periodo más amplio");
        }
        List<Periodo> periodos = Periodo.entre(desde, hasta, tipo);
        return new FiltroEvolucionConsumo(desde, hasta, tipo, categorias(categorias), periodos,
                periodoBase(periodoBase, periodos));
    }

    /** Posición del periodo base dentro de {@link #periodos()}. */
    public int indiceBase() {
        return periodos.stream().map(Periodo::clave).toList().indexOf(periodoBase);
    }

    private static Set<String> categorias(List<String> textos) {
        Set<String> categorias = new LinkedHashSet<>();
        for (String texto : textos == null ? List.<String>of() : textos) {
            String categoria = texto == null ? "" : texto.strip();
            if (categoria.isEmpty()) {
                throw new ReglaNegocioException("La categoría del filtro no puede estar vacía");
            }
            if (categoria.length() > LARGO_MAXIMO_CATEGORIA) {
                throw new ReglaNegocioException("La categoría '" + categoria.substring(0, 20)
                        + "…' supera los " + LARGO_MAXIMO_CATEGORIA + " caracteres");
            }
            categorias.add(categoria);
        }
        if (categorias.size() > MAXIMO_CATEGORIAS) {
            throw new ReglaNegocioException("Se pueden filtrar hasta " + MAXIMO_CATEGORIAS + " categorías");
        }
        return Set.copyOf(categorias);
    }

    private static String periodoBase(String texto, List<Periodo> periodos) {
        if (texto == null || texto.isBlank()) {
            return periodos.get(0).clave();
        }
        String clave = texto.strip();
        if (periodos.stream().noneMatch(p -> p.clave().equals(clave))) {
            throw new ReglaNegocioException("El periodo base '" + clave + "' no está dentro del rango consultado (de "
                    + periodos.get(0).clave() + " a " + periodos.get(periodos.size() - 1).clave() + ")");
        }
        return clave;
    }
}
