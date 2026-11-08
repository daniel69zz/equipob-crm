package com.maxiconecta.crm.fidelizacion.consulta;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Consulta del saldo, el nivel y los últimos movimientos de puntos de un cliente (SCRUM-47,
 * docs/fidelizacion/consulta-saldo-puntos.md).
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
                                                  int movimientos) {
        try {
            return consulta.consultar(clienteId, movimientos);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }
}
