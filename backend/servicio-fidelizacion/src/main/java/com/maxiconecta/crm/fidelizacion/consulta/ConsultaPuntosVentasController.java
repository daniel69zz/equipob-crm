package com.maxiconecta.crm.fidelizacion.consulta;

import com.maxiconecta.crm.fidelizacion.comun.IdentidadGateway;
import com.maxiconecta.crm.fidelizacion.consulta.ConsultaPuntosVentas.PuntosParaVentas;
import com.maxiconecta.crm.fidelizacion.movimiento.ClientesPerfil.PerfilNoDisponibleException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * RIO-CRM-04 · Consulta de solo lectura que Marketplace y Ventas invoca antes de aplicar una redención
 * (SCRUM-544, docs/contratos-eventos/RIO-CRM-04-consulta-puntos.md). Solo con el token de servicio de Ventas:
 * el Gateway lo exige (SCRUM-545) y el servicio lo vuelve a comprobar con la identidad que recibe.
 */
@RestController
@RequestMapping("/api/fidelizacion/ventas/clientes")
public class ConsultaPuntosVentasController {

    static final int LARGO_MAXIMO_ID_CLIENTE = 64;

    private final ConsultaPuntosVentas consulta;

    public ConsultaPuntosVentasController(ConsultaPuntosVentas consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/{idClienteVentas}/puntos")
    public PuntosParaVentas puntos(@PathVariable String idClienteVentas, HttpServletRequest solicitud) {
        IdentidadGateway.sistemaVentas(solicitud);
        String id = idClienteVentas.strip();
        if (id.isEmpty() || id.length() > LARGO_MAXIMO_ID_CLIENTE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "idClienteVentas debe tener entre 1 y " + LARGO_MAXIMO_ID_CLIENTE + " caracteres");
        }
        try {
            return consulta.consultar(id);
        } catch (PerfilNoDisponibleException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo vincular el cliente con su perfil del CRM; no aplique la redención y reintente");
        }
    }
}
