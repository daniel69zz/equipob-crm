package com.maxiconecta.crm.segmentacion.criterio;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import static com.maxiconecta.crm.segmentacion.criterio.Criterio.*;

/** Semántica común a validación, solapamiento y evaluación; reutilizable por SCRUM-26. */
public final class ReglasCriterio {
    private ReglasCriterio() { }

    public static String limpiar(String texto) {
        return texto == null ? "" : texto.strip().replaceAll("\\s+", " ");
    }

    public static String clave(String texto) {
        return Normalizer.normalize(limpiar(texto), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    public static SolicitudCriterio normalizar(SolicitudCriterio s) {
        if (s.indicador() == null || s.condicion() == null || limpiar(s.nombre()).isEmpty()) {
            throw invalido("Nombre, indicador y condición son obligatorios");
        }
        BigDecimal min = s.minimo(), max = s.maximo();
        String categoria = limpiar(s.categoria());
        String clasificacion = limpiar(s.clasificacion()).toUpperCase(Locale.ROOT);
        if (s.indicador() == Indicador.CLASIFICACION_FRECUENCIA) {
            // SCRUM-38: una sola clasificación por criterio, sin rangos ni categoría.
            if (s.condicion() != Condicion.IGUAL || !CLASIFICACIONES.contains(clasificacion)
                    || min != null || max != null || !categoria.isEmpty()) {
                throw invalido("La clasificación por frecuencia requiere la condición IGUAL y una clasificación: "
                        + "OCASIONAL, HABITUAL o FRECUENTE; no admite rangos ni categoría");
            }
            return new SolicitudCriterio(limpiar(s.nombre()), limpiar(s.descripcion()), s.indicador(),
                    s.condicion(), null, null, null, s.version(), clasificacion);
        }
        if (!clasificacion.isEmpty()) throw invalido("Solo la clasificación por frecuencia admite una clasificación");
        if (s.indicador() == Indicador.CATEGORIA) {
            if (s.condicion() != Condicion.CONTIENE || categoria.isEmpty() || min != null || max != null) {
                throw invalido("Categoría requiere la condición CONTIENE y una categoría; no admite rangos numéricos");
            }
        } else {
            if (!categoria.isEmpty()) throw invalido("Un criterio numérico no admite categoría");
            switch (s.condicion()) {
                case ENTRE -> { if (min == null || max == null) throw invalido("Indique los límites mínimo y máximo"); }
                case MAYOR_IGUAL -> { if (min == null || max != null) throw invalido("Mayor o igual requiere solo el mínimo"); }
                case MENOR_IGUAL -> { if (max == null || min != null) throw invalido("Menor o igual requiere solo el máximo"); }
                case IGUAL -> {
                    if (min == null || (max != null && min.compareTo(max) != 0)) throw invalido("Igual requiere un único valor");
                    max = min;
                }
                default -> throw invalido("La condición CONTIENE solo se usa con categorías");
            }
            if ((min != null && min.signum() < 0) || (max != null && max.signum() < 0)) {
                throw invalido("Los límites no pueden ser negativos");
            }
            if (min != null && max != null && min.compareTo(max) > 0) {
                throw invalido("El mínimo no puede superar al máximo");
            }
            if (s.indicador() == Indicador.FRECUENCIA &&
                    ((min != null && min.stripTrailingZeros().scale() > 0) ||
                     (max != null && max.stripTrailingZeros().scale() > 0))) {
                throw invalido("La frecuencia se expresa en compras enteras");
            }
        }
        return new SolicitudCriterio(limpiar(s.nombre()), limpiar(s.descripcion()), s.indicador(),
                s.condicion(), min, max, categoria.isEmpty() ? null : categoria, s.version());
    }

    public static boolean seSuperponen(SolicitudCriterio nuevo, Criterio existente) {
        if (!existente.activo() || nuevo.indicador() != existente.indicador()) return false;
        if (nuevo.indicador() == Indicador.CATEGORIA) return clave(nuevo.categoria()).equals(clave(existente.categoria()));
        if (nuevo.indicador() == Indicador.CLASIFICACION_FRECUENCIA) {
            return nuevo.clasificacion().equals(existente.clasificacion());
        }
        // Límites inclusivos: [0, 5] y [5, 10] comparten el 5 y se rechazan.
        return (nuevo.maximo() == null || existente.minimo() == null || nuevo.maximo().compareTo(existente.minimo()) >= 0)
                && (existente.maximo() == null || nuevo.minimo() == null || existente.maximo().compareTo(nuevo.minimo()) >= 0);
    }

    public static boolean cumple(Criterio criterio, BigDecimal frecuencia, BigDecimal valor, List<String> categorias) {
        return cumple(criterio, frecuencia, valor, categorias, null);
    }

    /** Igual que la anterior, con la clasificación por frecuencia del cliente (SCRUM-38), o {@code null} si no tiene. */
    public static boolean cumple(Criterio criterio, BigDecimal frecuencia, BigDecimal valor, List<String> categorias,
                                 String clasificacion) {
        if (!criterio.activo()) return false;
        if (criterio.indicador() == Indicador.CLASIFICACION_FRECUENCIA) {
            return clasificacion != null && clasificacion.equals(criterio.clasificacion());
        }
        if (criterio.indicador() == Indicador.CATEGORIA) {
            return categorias != null && categorias.stream().anyMatch(c -> clave(c).equals(clave(criterio.categoria())));
        }
        BigDecimal dato = criterio.indicador() == Indicador.FRECUENCIA ? frecuencia : valor;
        return dato != null && (criterio.minimo() == null || dato.compareTo(criterio.minimo()) >= 0)
                && (criterio.maximo() == null || dato.compareTo(criterio.maximo()) <= 0);
    }

    private static ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
