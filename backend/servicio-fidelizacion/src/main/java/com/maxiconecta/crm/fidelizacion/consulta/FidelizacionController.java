package com.maxiconecta.crm.fidelizacion.consulta;

import com.maxiconecta.crm.fidelizacion.puntos.SaldoPuntosRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta del saldo y nivel de puntos de un cliente (SCRUM-154, RIO-CRM-04). El API Gateway
 * exige PUNTOS_CONSULTAR para GET /api/fidelizacion/** y audita cada consulta
 * (docs/seguridad/convencion-rutas-clientes.md).
 */
@RestController
@RequestMapping("/api/fidelizacion/clientes")
public class FidelizacionController {

    private final SaldoPuntosRepository saldos;

    public FidelizacionController(SaldoPuntosRepository saldos) {
        this.saldos = saldos;
    }

    @GetMapping("/{clienteId}/puntos")
    public PuntosResponse obtener(@PathVariable Long clienteId) {
        return PuntosResponse.de(saldos.findById(clienteId));
    }
}
