package com.maxiconecta.crm.perfil.consulta;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ficha integral del cliente (SCRUM-10). El API Gateway exige FICHA_INTEGRAL_CONSULTAR para
 * GET /api/perfil/clientes/{clienteId}/ficha-integral —solo Administrador de CRM y Agente de
 * Atención al Cliente (SCRUM-153)— y audita cada consulta (docs/seguridad/convencion-rutas-clientes.md).
 */
@RestController
@RequestMapping("/api/perfil/clientes")
public class FichaIntegralController {

    private final ConsultaFichaIntegral consulta;

    public FichaIntegralController(ConsultaFichaIntegral consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/{clienteId}/ficha-integral")
    public FichaIntegralResponse obtener(@PathVariable Long clienteId) {
        return consulta.obtener(clienteId);
    }
}
