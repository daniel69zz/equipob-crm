package com.maxiconecta.crm.segmentacion.reporte;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reporte de distribución de clientes por segmento, nivel de consumo y nivel de fidelización
 * (SCRUM-57, docs/segmentacion/reportes-distribucion.md). Los dos filtros son opcionales: sin
 * ninguno el reporte cubre a todos los clientes conocidos. El API Gateway exige
 * {@code REPORTES_CONSULTAR} para {@code GET /api/segmentacion/reportes/**} y reenvía la identidad
 * del usuario en {@code X-Usuario}; el servicio la vuelve a comprobar en {@link PermisoReportes}.
 */
@RestController
@RequestMapping("/api/segmentacion/reportes")
public class ReportesController {

    private final ServicioReporteDistribucion servicio;

    public ReportesController(ServicioReporteDistribucion servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/distribucion")
    public ReporteDistribucion distribucion(@RequestParam(required = false) String segmento,
                                            @RequestParam(required = false) String nivelConsumo,
                                            @RequestHeader("X-Usuario") String usuario) {
        return servicio.generar(FiltroReporte.de(segmento, nivelConsumo), usuario);
    }
}
