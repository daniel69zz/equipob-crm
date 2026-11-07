package com.maxiconecta.crm.fidelizacion.regla;

import com.maxiconecta.crm.fidelizacion.comun.IdentidadGateway;
import com.maxiconecta.crm.fidelizacion.comun.Validaciones;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API de la regla de acumulación de puntos (SCRUM-392). El API Gateway exige {@code PUNTOS_CONSULTAR}
 * para los GET y {@code FIDELIZACION_CONFIGURAR} para el PUT; además, el servicio exige el rol de
 * Administrador de CRM para cambiar la regla y para ver su historial (criterio de aceptación 4). El
 * acceso se comprueba antes de validar el contenido.
 * <p>
 * La regla no se elimina: se reemplaza por una versión nueva y la anterior queda en el historial.
 */
@RestController
@RequestMapping("/api/fidelizacion/reglas/acumulacion")
public class ReglasAcumulacionController {

    private final ServicioReglasAcumulacion servicio;
    private final Validaciones validaciones;

    public ReglasAcumulacionController(ServicioReglasAcumulacion servicio, Validaciones validaciones) {
        this.servicio = servicio;
        this.validaciones = validaciones;
    }

    @GetMapping
    public ReglaAcumulacion vigente() {
        return servicio.vigente();
    }

    @PutMapping
    public ReglaAcumulacion actualizar(@RequestBody SolicitudReglaAcumulacion solicitud, HttpServletRequest req) {
        String usuario = IdentidadGateway.administradorFidelizacion(req, "cambiar la regla de acumulación de puntos");
        validaciones.validar(solicitud, "La regla de acumulación no es válida");
        return servicio.actualizar(solicitud, usuario);
    }

    @GetMapping("/historial")
    public List<ReglasAcumulacion.CambioRegla> historial(HttpServletRequest req) {
        IdentidadGateway.administradorFidelizacion(req, "consultar el historial de la regla de acumulación");
        return servicio.historial();
    }
}
