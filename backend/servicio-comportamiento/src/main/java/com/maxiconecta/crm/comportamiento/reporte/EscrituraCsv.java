package com.maxiconecta.crm.comportamiento.reporte;

import java.math.BigDecimal;
import java.util.List;

/**
 * Escribe filas en CSV con el formato estándar de los reportes (SCRUM-479, docs/compra/exportacion-reportes.md):
 * RFC 4180 con separador coma, fin de línea CRLF y campos con coma, comillas o saltos de línea entre comillas
 * dobles. Empieza con la marca de orden de bytes UTF-8 para que Excel muestre bien las tildes.
 */
final class EscrituraCsv {

    static final String BOM = "﻿";
    private static final String FIN = "\r\n";

    private EscrituraCsv() {
    }

    static String escribir(List<List<String>> filas) {
        StringBuilder csv = new StringBuilder(BOM);
        for (List<String> fila : filas) {
            csv.append(String.join(",", fila.stream().map(EscrituraCsv::campo).toList())).append(FIN);
        }
        return csv.toString();
    }

    /** Un número tal cual lo devuelve el JSON, sin notación científica; vacío si es nulo. */
    static String numero(BigDecimal valor) {
        return valor == null ? "" : valor.toPlainString();
    }

    static String campo(String texto) {
        if (texto == null) {
            return "";
        }
        if (texto.contains(",") || texto.contains("\"") || texto.contains("\n") || texto.contains("\r")) {
            return "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }
}
