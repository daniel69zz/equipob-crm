package com.maxiconecta.crm.interacciones.historial;

import com.maxiconecta.crm.interacciones.comun.IdentidadGateway;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/interacciones/clientes/{clienteId}/historial} (SCRUM-440): la línea de tiempo del cliente para
 * atenderlo. Va bajo el cliente, así que el Gateway audita la consulta (docs/seguridad/convencion-rutas-clientes.md).
 */
@RestController
public class HistorialClienteController {

    private final ServicioHistorialCliente servicio;

    public HistorialClienteController(ServicioHistorialCliente servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/api/interacciones/clientes/{clienteId}/historial")
    public HistorialCliente consultar(@PathVariable long clienteId, @RequestParam(required = false) Integer limite,
                                      HttpServletRequest http) {
        IdentidadGateway.consulta(http);
        return servicio.consultar(clienteId, limite);
    }
}
