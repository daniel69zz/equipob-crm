package com.maxiconecta.crm.comportamiento.reporte;

import com.maxiconecta.crm.comportamiento.comparacion.PeriodosAComparar;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Reporte comparativo de indicadores de clientes entre dos periodos (SCRUM-62,
 * docs/compra/reporte-comparativo.md) y su exportación a CSV. Como el reporte de SCRUM-58, devuelve solo
 * agregados; el API Gateway exige {@code REPORTES_CONSULTAR} para {@code GET /api/comportamiento/reportes/**}
 * y el servicio lo vuelve a comprobar en {@link PermisoReportes} (SCRUM-500).
 */
@RestController
@RequestMapping("/api/comportamiento/reportes/comparativo")
public class ReporteComparativoController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ConsultaReporteComparativo consulta;
    private final ExportacionComparativoCsv csv;

    public ReporteComparativoController(ConsultaReporteComparativo consulta, ExportacionComparativoCsv csv) {
        this.consulta = consulta;
        this.csv = csv;
    }

    /** Periodos superpuestos, incompletos o al revés responden 400 con el motivo (SCRUM-494). */
    @GetMapping
    public ReporteComparativo comparar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desdeA,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hastaA,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desdeB,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hastaB,
            @RequestParam(required = false) String segmento) {
        return consulta.generar(PeriodosAComparar.de(desdeA, hastaA, desdeB, hastaB), segmento);
    }

    /** El mismo reporte en CSV, como archivo descargable (SCRUM-499, criterio de aceptación 3). */
    @GetMapping("/csv")
    public ResponseEntity<String> exportarCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desdeA,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hastaA,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desdeB,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hastaB,
            @RequestParam(required = false) String segmento) {
        PeriodosAComparar periodos = PeriodosAComparar.de(desdeA, hastaA, desdeB, hastaB);
        ReporteComparativo reporte = consulta.generar(periodos, segmento);
        String archivo = "reporte-comparativo_" + periodos.a().desde() + "_" + periodos.a().hasta() + "_vs_"
                + periodos.b().desde() + "_" + periodos.b().hasta() + ".csv";
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(archivo).build().toString())
                .body(csv.exportar(reporte));
    }
}
