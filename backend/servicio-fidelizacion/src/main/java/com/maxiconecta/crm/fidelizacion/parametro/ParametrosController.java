package com.maxiconecta.crm.fidelizacion.parametro;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API de los parámetros generales de servicio-fidelizacion (SCRUM-65): la vigencia de los puntos. El
 * acceso se comprueba antes de validar el contenido, así que un rol sin permiso recibe 403 aunque la
 * solicitud además sea inválida.
 */
@RestController
@RequestMapping("/api/fidelizacion/parametros")
public class ParametrosController {

    private final ParametrosGenerales parametros;

    public ParametrosController(ParametrosGenerales parametros) {
        this.parametros = parametros;
    }

    @GetMapping
    public List<Parametro> listar(HttpServletRequest solicitud) {
        AccesoParametros.administrador(solicitud);
        return parametros.listar();
    }

    /** Guarda los cambios y devuelve los parámetros como quedaron, con sus versiones nuevas. */
    @PutMapping
    public List<Parametro> actualizar(@RequestBody(required = false) SolicitudParametros solicitud,
                                      HttpServletRequest req) {
        String usuario = AccesoParametros.administrador(req);
        parametros.actualizar(solicitud == null ? null : solicitud.cambios(), usuario);
        return parametros.listar();
    }
}
