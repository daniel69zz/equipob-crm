package com.maxiconecta.crm.comportamiento.consumo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;

/** Contrato interno de lectura. El Gateway no enruta /internal/**. */
@RestController
public class UmbralesConsumoController {
    private final CriterioNivelConsumo criterio;

    public UmbralesConsumoController(CriterioNivelConsumo criterio) {
        this.criterio = criterio;
    }

    @GetMapping("/internal/consumo/umbrales")
    public Umbrales obtener() {
        return new Umbrales(criterio.umbralMedio(), criterio.umbralAlto());
    }

    public record Umbrales(BigDecimal medio, BigDecimal alto) { }
}
