package com.maxiconecta.crm.interacciones.notificacion;

import com.maxiconecta.crm.interacciones.comun.IdentidadGateway;
import com.maxiconecta.crm.interacciones.comun.IdentidadGateway.Identidad;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Notificaciones del usuario de la sesión (SCRUM-459): cada uno ve y marca solo las suyas. No son rutas de cliente
 * (docs/seguridad/convencion-rutas-clientes.md, regla 5).
 */
@RestController
@RequestMapping("/api/interacciones/notificaciones")
public class NotificacionesController {

    static final int LIMITE = 50;

    private final NotificacionesUsuario notificaciones;

    public NotificacionesController(NotificacionesUsuario notificaciones) {
        this.notificaciones = notificaciones;
    }

    @GetMapping
    public Bandeja consultar(@RequestParam(defaultValue = "false") boolean soloNoLeidas, HttpServletRequest http) {
        Identidad identidad = IdentidadGateway.consulta(http);
        return new Bandeja(notificaciones.de(identidad.usuario(), soloNoLeidas, LIMITE),
                notificaciones.noLeidas(identidad.usuario()));
    }

    /** Una notificación de otro usuario responde 404, igual que una que no existe. */
    @PostMapping("/{id}/leida")
    public Notificacion marcarLeida(@PathVariable long id, HttpServletRequest http) {
        Identidad identidad = IdentidadGateway.consulta(http);
        return notificaciones.marcarLeida(id, identidad.usuario()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No tiene la notificación " + id));
    }

    /** Las notificaciones más recientes del usuario y cuántas no leyó. */
    public record Bandeja(List<Notificacion> notificaciones, long noLeidas) {
    }
}
