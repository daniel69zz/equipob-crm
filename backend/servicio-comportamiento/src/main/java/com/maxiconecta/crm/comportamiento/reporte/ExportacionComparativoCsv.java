package com.maxiconecta.crm.comportamiento.reporte;

import com.maxiconecta.crm.comportamiento.reporte.ReporteComparativo.Indicador;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Exporta el reporte comparativo a CSV (SCRUM-499, docs/compra/reporte-comparativo.md). Se escribe a partir
 * del mismo {@link ReporteComparativo} que devuelve el JSON, sin volver a calcular nada, así que conserva los
 * mismos valores (criterio de aceptación 3).
 *
 * <p>Formato RFC 4180: separador coma, punto decimal, sin separador de miles, campos con coma, comillas o
 * saltos de línea entre comillas dobles, y fin de línea CRLF. Empieza con la marca de orden de bytes UTF-8
 * para que Excel muestre bien las tildes.
 */
@Component
public class ExportacionComparativoCsv {

    static final String BOM = "﻿";
    private static final String FIN = "\r\n";

    public String exportar(ReporteComparativo reporte) {
        List<List<String>> filas = new ArrayList<>();
        filas.add(List.of("Reporte comparativo de indicadores de clientes"));
        filas.add(List.of("Alcance", reporte.alcance().segmento() == null ? "Toda la cartera"
                : "Segmento " + reporte.alcance().segmento()));
        filas.add(List.of("Periodo A", reporte.periodoA().desde() + " a " + reporte.periodoA().hasta()));
        filas.add(List.of("Periodo B", reporte.periodoB().desde() + " a " + reporte.periodoB().hasta()));
        filas.add(List.of("Zona horaria", reporte.zonaHoraria()));
        filas.add(List.of("Generado", reporte.generadoEn().toString()));
        filas.add(List.of());
        filas.add(List.of("Indicador", "Codigo", "Periodo A", "Periodo B", "Variacion", "Variacion %", "Tendencia"));
        for (Indicador i : reporte.indicadores()) {
            filas.add(List.of(i.nombre(), i.codigo(), numero(i.periodoA()), numero(i.periodoB()),
                    numero(i.variacionAbsoluta()), numero(i.variacionPorcentual()),
                    i.tendencia() == null ? "" : i.tendencia().name()));
        }
        StringBuilder csv = new StringBuilder(BOM);
        for (List<String> fila : filas) {
            csv.append(String.join(",", fila.stream().map(ExportacionComparativoCsv::campo).toList())).append(FIN);
        }
        return csv.toString();
    }

    /** Un número tal cual lo devuelve el JSON, sin notación científica; vacío si es nulo. */
    private static String numero(BigDecimal valor) {
        return valor == null ? "" : valor.toPlainString();
    }

    private static String campo(String texto) {
        if (texto.contains(",") || texto.contains("\"") || texto.contains("\n") || texto.contains("\r")) {
            return "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }
}
