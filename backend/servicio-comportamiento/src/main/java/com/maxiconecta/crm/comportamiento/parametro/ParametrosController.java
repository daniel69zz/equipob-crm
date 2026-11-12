package com.maxiconecta.crm.comportamiento.parametro;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API de los parámetros generales de servicio-comportamiento (SCRUM-65): periodo de inactividad y umbrales de
 * consumo. El acceso se comprueba antes de validar el contenido, así que un rol sin permiso recibe 403 aunque la
 * solicitud además sea inválida.
 */
@RestController
@RequestMapping("/api/comportamiento/parametros")
public class ParametrosController {

    /** Cambios que muestra el historial de la pantalla. */
    static final int LIMITE_HISTORIAL = 100;

    private final ParametrosGenerales parametros;
    private final AuditoriaParametros auditoria;

    public ParametrosController(ParametrosGenerales parametros, AuditoriaParametros auditoria) {
        this.parametros = parametros;
        this.auditoria = auditoria;
    }

    @GetMapping
    public List<Parametro> listar(HttpServletRequest solicitud) {
        AccesoParametros.administrador(solicitud);
        return parametros.listar();
    }

    /** SCRUM-521: los últimos cambios de estos parámetros con el valor anterior, el nuevo, quién y cuándo. */
    @GetMapping("/historial")
    public List<AuditoriaParametros.CambioRegistrado> historial(HttpServletRequest solicitud) {
        AccesoParametros.administrador(solicitud);
        return auditoria.historial(parametros.codigos(), LIMITE_HISTORIAL);
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
