package com.maxiconecta.crm.fidelizacion.caducidad;

import com.maxiconecta.crm.fidelizacion.comun.IdentidadGateway;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Caducidad de puntos (SCRUM-50, docs/fidelizacion/caducidad-puntos.md). El API Gateway exige
 * {@code PUNTOS_CONSULTAR} para los GET y {@code FIDELIZACION_CONFIGURAR} para el resto.
 */
@RestController
@RequestMapping("/api/fidelizacion/caducidad")
public class CaducidadController {

    private final VigenciaPuntos vigencia;

    public CaducidadController(VigenciaPuntos vigencia) {
        this.vigencia = vigencia;
    }

    /** SCRUM-416: vigencia de los puntos y horario del proceso de caducidad. */
    @GetMapping("/configuracion")
    public VigenciaPuntos.Configuracion configuracion(HttpServletRequest req) {
        IdentidadGateway.consultaPuntos(req);
        return vigencia.configuracion();
    }
}
