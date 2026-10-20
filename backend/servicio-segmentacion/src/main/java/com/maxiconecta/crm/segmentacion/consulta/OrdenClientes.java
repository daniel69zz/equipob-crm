package com.maxiconecta.crm.segmentacion.consulta;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Columnas por las que se puede ordenar el listado de clientes por segmento (SCRUM-273,
 * docs/segmentacion/criterio-consulta-clientes.md). Cada una trae su expresión SQL fija: el
 * parámetro {@code orden} solo elige entre ellas, nunca se concatena texto del usuario.
 */
public enum OrdenClientes {
    VALOR("valor", "valor"),
    NIVEL_CONSUMO("nivelConsumo", "CASE nivel_consumo WHEN 'BAJO' THEN 1 WHEN 'MEDIO' THEN 2 WHEN 'ALTO' THEN 3 END"),
    ULTIMA_COMPRA("ultimaCompra", "ultima_compra"),
    FRECUENCIA("frecuencia", "frecuencia"),
    SEGMENTO("segmento", "segmento"),
    ASIGNADO_EN("asignadoEn", "asignado_en");

    /** Sin {@code orden}, del cliente que más aporta al que menos. */
    public static final OrdenClientes POR_DEFECTO = VALOR;

    private final String parametro;
    private final String expresion;

    OrdenClientes(String parametro, String expresion) {
        this.parametro = parametro;
        this.expresion = expresion;
    }

    /** Nombre con el que se pide en la consulta ({@code ?orden=ultimaCompra}). */
    public String parametro() {
        return parametro;
    }

    /**
     * Cláusula ORDER BY completa: los clientes sin dato van al final en los dos sentidos y, a igual
     * valor, se desempata por número de cliente para que la paginación sea estable.
     */
    String ordenSql(Direccion direccion) {
        return expresion + " " + direccion.name() + " NULLS LAST, cliente_id ASC";
    }

    public static OrdenClientes de(String texto) {
        if (texto == null || texto.isBlank()) {
            return POR_DEFECTO;
        }
        return Arrays.stream(values())
                .filter(orden -> orden.parametro.equalsIgnoreCase(texto.strip()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Orden inválido: '" + texto
                        + "' (se espera uno de " + Arrays.stream(values()).map(OrdenClientes::parametro)
                        .collect(Collectors.joining(", ")) + ")"));
    }

    /** Sentido del orden; sin {@code direccion}, descendente. */
    public enum Direccion {
        ASC,
        DESC;

        public static Direccion de(String texto) {
            if (texto == null || texto.isBlank()) {
                return DESC;
            }
            return Arrays.stream(values())
                    .filter(direccion -> direccion.name().equalsIgnoreCase(texto.strip()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Dirección inválida: '" + texto + "' (se espera asc o desc)"));
        }

        /** Nombre con el que se pide y se devuelve ({@code asc} o {@code desc}). */
        public String parametro() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
