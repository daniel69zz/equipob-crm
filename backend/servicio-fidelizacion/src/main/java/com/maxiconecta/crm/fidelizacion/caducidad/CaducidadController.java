package com.maxiconecta.crm.fidelizacion.caducidad;

import com.maxiconecta.crm.fidelizacion.comun.IdentidadGateway;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final ProcesoCaducidad proceso;

    public CaducidadController(VigenciaPuntos vigencia, ProcesoCaducidad proceso) {
        this.vigencia = vigencia;
        this.proceso = proceso;
    }

    /** SCRUM-416: vigencia de los puntos y horario del proceso de caducidad. */
    @GetMapping("/configuracion")
    public VigenciaPuntos.Configuracion configuracion(HttpServletRequest req) {
        IdentidadGateway.consultaPuntos(req);
        return vigencia.configuracion();
    }

    /** SCRUM-415: ejecuta ahora el proceso de caducidad (lo mismo que hace el proceso automático cada día). */
    @PostMapping("/ejecutar")
    public ProcesoCaducidad.Ejecucion ejecutar(HttpServletRequest req) {
        IdentidadGateway.administradorFidelizacion(req, "ejecutar la caducidad de puntos");
        return proceso.caducarVencidos();
    }
}
