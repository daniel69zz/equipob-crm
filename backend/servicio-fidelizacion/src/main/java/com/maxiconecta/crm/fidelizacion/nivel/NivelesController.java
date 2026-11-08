package com.maxiconecta.crm.fidelizacion.nivel;

import com.maxiconecta.crm.fidelizacion.comun.IdentidadGateway;
import com.maxiconecta.crm.fidelizacion.comun.Validaciones;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Gestión de los niveles de fidelización (SCRUM-413). El API Gateway exige {@code PUNTOS_CONSULTAR} para
 * los GET y {@code FIDELIZACION_CONFIGURAR} para el resto; el servicio exige además el rol de
 * Administrador de CRM para cambiar, reclasificar y ver el historial.
 */
@RestController
@RequestMapping("/api/fidelizacion/niveles")
public class NivelesController {

    private final ServicioNiveles servicio;
    private final Validaciones validaciones;

    public NivelesController(ServicioNiveles servicio, Validaciones validaciones) {
        this.servicio = servicio;
        this.validaciones = validaciones;
    }

    @GetMapping
    public ConfiguracionNiveles vigente() {
        return servicio.vigente();
    }

    @PutMapping
    public ServicioNiveles.Resultado actualizar(@RequestBody SolicitudNiveles solicitud, HttpServletRequest req) {
        String usuario = IdentidadGateway.administradorFidelizacion(req, "configurar los niveles de fidelización");
        validaciones.validar(solicitud, "Los niveles no son válidos");
        return servicio.actualizar(solicitud, usuario);
    }

    @GetMapping("/historial")
    public List<ConfiguracionNiveles> historial(HttpServletRequest req) {
        IdentidadGateway.administradorFidelizacion(req, "consultar el historial de los niveles");
        return servicio.historial();
    }

    /** Reclasifica ahora a todos los clientes (lo mismo que hace el recálculo programado cada noche). */
    @PostMapping("/recalcular")
    public Reclasificacion recalcular(HttpServletRequest req) {
        IdentidadGateway.administradorFidelizacion(req, "reclasificar a los clientes por nivel");
        return new Reclasificacion(servicio.recalcularTodos());
    }

    public record Reclasificacion(int clientesReclasificados) {
    }
}
