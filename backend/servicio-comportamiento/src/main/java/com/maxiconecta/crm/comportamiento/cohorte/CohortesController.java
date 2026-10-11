package com.maxiconecta.crm.comportamiento.cohorte;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Análisis de retención por cohortes (SCRUM-329, SCRUM-36). No es un dato de un cliente, así que no
 * va bajo /clientes; el API Gateway ya exige INDICADORES_CONSULTAR a todo GET /api/comportamiento/**.
 */
@RestController
public class CohortesController {

    private final AnalisisCohortes analisis;

    public CohortesController(AnalisisCohortes analisis) {
        this.analisis = analisis;
    }

    @GetMapping("/api/comportamiento/cohortes")
    public AnalisisCohortes.MatrizRetencion matriz(@RequestParam(required = false) String desde,
                                                   @RequestParam(required = false) String hasta) {
        return analisis.analizar(desde, hasta);
    }
}
