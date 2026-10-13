package com.maxiconecta.crm.segmentacion.asignacion;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

/**
 * Reproceso de los mensajes de métricas que quedaron en la cola de respaldo (SCRUM-241). El API
 * Gateway exige SEGMENTACION_CONFIGURAR para las escrituras en /api/segmentacion/**; el servicio
 * vuelve a comprobar la identidad que el Gateway agrega, como en los criterios.
 */
@RestController
@RequestMapping("/api/segmentacion/respaldo")
public class RespaldoController {

    private final ReinyectorRespaldo reinyector;

    public RespaldoController(ReinyectorRespaldo reinyector) {
        this.reinyector = reinyector;
    }

    /** Reinyecta un mensaje; 204 si la cola de respaldo está vacía. */
    @PostMapping("/reinyectar")
    public ResponseEntity<ReinyectorRespaldo.Reinyeccion> reinyectar(HttpServletRequest req) {
        return reinyector.reinyectarUno(administrador(req))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private static String administrador(HttpServletRequest req) {
        String usuario = req.getHeader("X-Usuario"), permisos = req.getHeader("X-Usuario-Permisos");
        if (usuario == null || usuario.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere la identidad del Gateway");
        }
        if (permisos == null || Arrays.stream(permisos.split(",")).noneMatch("SEGMENTACION_CONFIGURAR"::equals)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el Administrador de CRM puede reprocesar mensajes");
        }
        return usuario;
    }
}
