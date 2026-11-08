package com.maxiconecta.crm.fidelizacion.ajuste;

import com.maxiconecta.crm.fidelizacion.comun.IdentidadGateway;
import com.maxiconecta.crm.fidelizacion.comun.Validaciones;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Ajuste manual del saldo de puntos de un cliente (SCRUM-423, docs/fidelizacion/ajuste-manual-puntos.md). El
 * acceso se comprueba antes de validar el cuerpo, para que un usuario sin permiso reciba 403 aunque su solicitud
 * además sea inválida.
 */
@RestController
@RequestMapping("/api/fidelizacion/clientes/{clienteId}/ajustes")
public class AjustesController {

    private final ServicioAjustes servicio;
    private final Validaciones validaciones;

    public AjustesController(ServicioAjustes servicio, Validaciones validaciones) {
        this.servicio = servicio;
        this.validaciones = validaciones;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Ajuste ajustar(@PathVariable long clienteId, @RequestBody(required = false) SolicitudAjuste solicitud,
                          HttpServletRequest req) {
        String usuario = IdentidadGateway.administradorFidelizacion(req, "ajustar el saldo de puntos de un cliente");
        validaciones.validar(solicitud, "El ajuste no es válido");
        return servicio.ajustar(clienteId, solicitud, usuario, req.getHeader(IdentidadGateway.ROL));
    }

    /** Historial de ajustes del cliente, para su ficha (criterio de aceptación 4). */
    @GetMapping
    public List<Ajuste> historial(@PathVariable long clienteId, HttpServletRequest req) {
        IdentidadGateway.consultaPuntos(req);
        return servicio.historial(clienteId);
    }
}
