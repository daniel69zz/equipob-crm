package com.maxiconecta.crm.interacciones.compras;

import com.maxiconecta.crm.interacciones.comun.IdentidadGateway;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/interacciones/clientes/{clienteId}/compras} (SCRUM-446): las compras del cliente para elegir la que
 * se vincula a una interacción. Va bajo el cliente, así que el Gateway audita la consulta
 * (docs/seguridad/convencion-rutas-clientes.md).
 */
@RestController
public class ComprasElegiblesController {

    private final ServicioComprasElegibles servicio;

    public ComprasElegiblesController(ServicioComprasElegibles servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/api/interacciones/clientes/{clienteId}/compras")
    public ComprasElegibles consultar(@PathVariable long clienteId, @RequestParam(required = false) String texto,
                                      @RequestParam(required = false) Integer limite, HttpServletRequest http) {
        // Los mismos permisos que el historial del cliente con sus compras (SCRUM-438).
        IdentidadGateway.historialCliente(http);
        return servicio.consultar(clienteId, texto, limite);
    }
}
