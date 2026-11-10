package com.maxiconecta.crm.comportamiento.reporte;

import com.maxiconecta.crm.comportamiento.comparacion.PeriodosAComparar;
import com.maxiconecta.crm.comportamiento.periodo.RangoPeriodo;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Exporta los reportes de clientes a CSV y PDF (SCRUM-60, docs/compra/exportacion-reportes.md).
 * Genera el reporte con la misma consulta que la pantalla y lo escribe tal cual, así que el archivo contiene
 * los mismos datos que se ven (criterio de aceptación 1). Cada exportación queda en la auditoría antes de entregar
 * el archivo ({@link AuditoriaExportaciones}, criterio de aceptación 3).
 */
@Service
public class ExportacionReportes {

    private final ConsultaReporteComportamiento comportamiento;
    private final ConsultaReporteComparativo comparativo;
    private final ExportacionComportamientoCsv comportamientoCsv;
    private final ExportacionComparativoCsv comparativoCsv;
    private final ExportacionComportamientoPdf comportamientoPdf;
    private final ExportacionComparativoPdf comparativoPdf;
    private final AuditoriaExportaciones auditoria;

    public ExportacionReportes(ConsultaReporteComportamiento comportamiento, ConsultaReporteComparativo comparativo,
                               ExportacionComportamientoCsv comportamientoCsv, ExportacionComparativoCsv comparativoCsv,
                               ExportacionComportamientoPdf comportamientoPdf, ExportacionComparativoPdf comparativoPdf,
                               AuditoriaExportaciones auditoria) {
        this.comportamiento = comportamiento;
        this.comparativo = comparativo;
        this.comportamientoCsv = comportamientoCsv;
        this.comparativoCsv = comparativoCsv;
        this.comportamientoPdf = comportamientoPdf;
        this.comparativoPdf = comparativoPdf;
        this.auditoria = auditoria;
    }

    /** Reporte de comportamiento de compra (SCRUM-58) de un periodo y, opcionalmente, un segmento. */
    public ArchivoExportado comportamiento(RangoPeriodo rango, String segmento, FormatoExportacion formato,
                                           String usuario) {
        ReporteComportamiento reporte = comportamiento.generar(rango, segmento);
        String nombre = "reporte-comportamiento_" + reporte.desde() + "_" + reporte.hasta()
                + sufijo(reporte.alcance().segmento()) + "." + formato.extension();
        byte[] contenido = switch (formato) {
            case CSV -> comportamientoCsv.exportar(reporte).getBytes(StandardCharsets.UTF_8);
            case PDF -> comportamientoPdf.exportar(reporte, usuario);
        };
        ArchivoExportado archivo = new ArchivoExportado(nombre, formato, contenido);
        auditoria.comportamiento(usuario, reporte, archivo);
        return archivo;
    }

    /** Reporte comparativo entre dos periodos (SCRUM-62). */
    public ArchivoExportado comparativo(PeriodosAComparar periodos, String segmento, FormatoExportacion formato,
                                        String usuario) {
        ReporteComparativo reporte = comparativo.generar(periodos, segmento);
        String nombre = "reporte-comparativo_" + reporte.periodoA().desde() + "_" + reporte.periodoA().hasta() + "_vs_"
                + reporte.periodoB().desde() + "_" + reporte.periodoB().hasta() + "." + formato.extension();
        byte[] contenido = switch (formato) {
            case CSV -> comparativoCsv.exportar(reporte).getBytes(StandardCharsets.UTF_8);
            case PDF -> comparativoPdf.exportar(reporte, usuario);
        };
        ArchivoExportado archivo = new ArchivoExportado(nombre, formato, contenido);
        auditoria.comparativo(usuario, reporte, archivo);
        return archivo;
    }

    /** {@code _alto-valor} para el segmento «Alto valor»: sin tildes, espacios ni caracteres fuera de [a-z0-9-]. */
    static String sufijo(String segmento) {
        if (segmento == null) {
            return "";
        }
        String limpio = Normalizer.normalize(segmento, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return limpio.isEmpty() ? "" : "_" + limpio;
    }
}
