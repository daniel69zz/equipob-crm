package com.maxiconecta.crm.comportamiento.reporte;

import com.maxiconecta.crm.comportamiento.comparacion.PeriodosAComparar;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Reporte comparativo de indicadores de clientes entre dos periodos (SCRUM-62,
 * docs/compra/reporte-comparativo.md) y su exportación (SCRUM-499 y SCRUM-60). Como el reporte de SCRUM-58,
 * devuelve solo agregados; el API Gateway exige {@code REPORTES_CONSULTAR} para
 * {@code GET /api/comportamiento/reportes/**} y el servicio lo vuelve a comprobar en {@link PermisoReportes}
 * (SCRUM-500).
 */
@RestController
@RequestMapping("/api/comportamiento/reportes/comparativo")
public class ReporteComparativoController {

    private final ConsultaReporteComparativo consulta;
    private final ExportacionReportes exportacion;

    public ReporteComparativoController(ConsultaReporteComparativo consulta, ExportacionReportes exportacion) {
        this.consulta = consulta;
        this.exportacion = exportacion;
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

    /** El mismo reporte como archivo descargable en el formato elegido (SCRUM-60). */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(required = false) String formato,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desdeA,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hastaA,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desdeB,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hastaB,
            @RequestParam(required = false) String segmento) {
        FormatoExportacion elegido = FormatoExportacion.de(formato);
        return exportacion.comparativo(PeriodosAComparar.de(desdeA, hastaA, desdeB, hastaB), segmento, elegido)
                .respuesta();
    }

    /** Ruta de SCRUM-499 (criterio 3 de SCRUM-62): la misma exportación en CSV. */
    @GetMapping("/csv")
    public ResponseEntity<byte[]> exportarCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desdeA,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hastaA,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desdeB,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hastaB,
            @RequestParam(required = false) String segmento) {
        return exportacion.comparativo(PeriodosAComparar.de(desdeA, hastaA, desdeB, hastaB), segmento,
                FormatoExportacion.CSV).respuesta();
    }
}
