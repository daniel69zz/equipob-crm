package com.maxiconecta.crm.comportamiento.parametro;

import java.math.BigDecimal;

/**
 * Un parámetro general del CRM (SCRUM-65): su código, cómo se muestra, el rango permitido y el valor con el que se
 * crea la primera vez, que es el de la configuración del servicio.
 *
 * @param decimales cuántos decimales admite; 0 para un entero
 */
public record DefinicionParametro(String codigo, String nombre, String descripcion, String unidad,
                                  BigDecimal minimo, BigDecimal maximo, int decimales, BigDecimal valorInicial) {

    public DefinicionParametro {
        if (minimo.compareTo(maximo) > 0) {
            throw new IllegalArgumentException("El mínimo de " + codigo + " supera al máximo");
        }
    }

    /** Un parámetro entero, como una cantidad de días o de meses. */
    public static DefinicionParametro entero(String codigo, String nombre, String descripcion, String unidad,
                                             int minimo, int maximo, int valorInicial) {
        return new DefinicionParametro(codigo, nombre, descripcion, unidad, BigDecimal.valueOf(minimo),
                BigDecimal.valueOf(maximo), 0, BigDecimal.valueOf(valorInicial));
    }

    /** El motivo por el que {@code valor} no es válido, o {@code null} si lo es. */
    String rechazo(BigDecimal valor) {
        if (valor == null) {
            return "es obligatorio";
        }
        if (valor.stripTrailingZeros().scale() > decimales) {
            return decimales == 0 ? "debe ser un número entero" : "admite hasta " + decimales + " decimales";
        }
        if (valor.compareTo(minimo) < 0 || valor.compareTo(maximo) > 0) {
            return "debe estar entre " + minimo.toPlainString() + " y " + maximo.toPlainString() + " " + unidad;
        }
        return null;
    }
}
