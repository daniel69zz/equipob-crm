package com.maxiconecta.crm.comportamiento.metrica;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Republicación manual de las métricas de todos los clientes ({@link RepublicacionMetricas}).
 * Va bajo /api/comportamiento/eventos/**, que el API Gateway reserva al permiso EVENTOS_REPROCESAR.
 */
@RestController
@RequestMapping("/api/comportamiento/eventos/metricas")
public class RepublicacionMetricasController {

    private final RepublicacionMetricas republicacion;

    public RepublicacionMetricasController(RepublicacionMetricas republicacion) {
        this.republicacion = republicacion;
    }

    @PostMapping("/republicar")
    public RepublicacionMetricas.Resultado republicar() {
        return republicacion.republicarTodos();
    }
}
