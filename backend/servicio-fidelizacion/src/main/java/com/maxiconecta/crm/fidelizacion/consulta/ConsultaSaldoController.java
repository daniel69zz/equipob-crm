package com.maxiconecta.crm.fidelizacion.consulta;

import com.maxiconecta.crm.fidelizacion.comun.IdentidadGateway;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Consulta del saldo, el nivel y los últimos movimientos de puntos de un cliente (SCRUM-47,
 * docs/fidelizacion/consulta-saldo-puntos.md). Exige la identidad del API Gateway y el permiso
 * {@code PUNTOS_CONSULTAR} (SCRUM-396).
 */
@RestController
@RequestMapping("/api/fidelizacion/clientes")
public class ConsultaSaldoController {

    private final ConsultaSaldoPuntos consulta;

    public ConsultaSaldoController(ConsultaSaldoPuntos consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/{clienteId}/saldo")
    public ConsultaSaldoPuntos.SaldoCliente saldo(@PathVariable long clienteId,
                                                  @RequestParam(defaultValue = "" + ConsultaSaldoPuntos.MOVIMIENTOS_POR_DEFECTO)
                                                  int movimientos, HttpServletRequest solicitud) {
        IdentidadGateway.consultaPuntos(solicitud);
        try {
            return consulta.consultar(clienteId, movimientos);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }
}
