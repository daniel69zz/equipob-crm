package com.maxiconecta.crm.comportamiento.reporte;

import com.maxiconecta.crm.comportamiento.periodo.RangoPeriodo;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Reporte de frecuencia, valor y categorías de compra filtrado por periodo y segmento (SCRUM-58,
 * docs/compra/reportes-comportamiento.md). Devuelve solo agregados, sin datos de clientes, así que va
 * fuera de {@code /clientes} (docs/seguridad/convencion-rutas-clientes.md). El API Gateway exige
 * {@code REPORTES_CONSULTAR} para {@code GET /api/comportamiento/reportes/**}, y el servicio lo vuelve
 * a comprobar en {@link PermisoReportes}.
 */
@RestController
@RequestMapping("/api/comportamiento/reportes")
public class ReporteComportamientoController {

    private final ConsultaReporteComportamiento consulta;

    public ReporteComportamientoController(ConsultaReporteComportamiento consulta) {
        this.consulta = consulta;
    }

    /** El periodo es obligatorio; un rango al revés o de más de cinco años responde 400 con el motivo. */
    @GetMapping("/comportamiento")
    public ReporteComportamiento comportamiento(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String segmento) {
        return consulta.generar(new RangoPeriodo(desde, hasta), segmento);
    }
}
