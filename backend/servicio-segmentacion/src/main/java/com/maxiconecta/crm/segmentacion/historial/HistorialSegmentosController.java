package com.maxiconecta.crm.segmentacion.historial;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

/** SCRUM-292. El Gateway valida el JWT y suministra la identidad en la red interna. */
@RestController
@RequestMapping("/api/segmentacion/clientes/{clienteId}/historial-segmentos")
public class HistorialSegmentosController {
    private final HistorialSegmentos historial;

    public HistorialSegmentosController(HistorialSegmentos historial) {
        this.historial = historial;
    }

    @GetMapping
    public HistorialSegmentos.Pagina consultar(@PathVariable long clienteId,
                                               @RequestParam(defaultValue = "0") int pagina,
                                               @RequestParam(defaultValue = "20") int tamanio,
                                               HttpServletRequest solicitud) {
        String usuario = solicitud.getHeader("X-Usuario");
        if (usuario == null || usuario.isBlank())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere la identidad del Gateway");
        String permisos = solicitud.getHeader("X-Usuario-Permisos");
        if (permisos == null || Arrays.stream(permisos.split(",")).map(String::trim)
                .noneMatch("SEGMENTOS_CONSULTAR"::equals))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere el permiso SEGMENTOS_CONSULTAR");
        if (clienteId <= 0 || pagina < 0 || tamanio < 1 || tamanio > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Indique un cliente positivo, una página no negativa y un tamaño entre 1 y 100");
        return historial.consultar(clienteId, pagina, tamanio);
    }
}
