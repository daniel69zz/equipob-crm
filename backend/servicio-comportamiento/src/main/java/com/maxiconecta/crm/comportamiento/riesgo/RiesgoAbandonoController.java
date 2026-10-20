package com.maxiconecta.crm.comportamiento.riesgo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Riesgo de abandono de un cliente del CRM con los factores que lo explican (SCRUM-325, SCRUM-35),
 * para la ficha integral. A diferencia de los indicadores, se busca por el número de cliente del CRM:
 * el riesgo ya suma todos sus identificadores. El API Gateway exige INDICADORES_CONSULTAR y audita el
 * acceso con el {clienteId} de la ruta.
 */
@RestController
@RequestMapping("/api/comportamiento/clientes")
public class RiesgoAbandonoController {

    private final ConsultaRiesgoAbandono consulta;

    public RiesgoAbandonoController(ConsultaRiesgoAbandono consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/{clienteId}/riesgo-abandono")
    public ConsultaRiesgoAbandono.RiesgoAbandono buscar(@PathVariable long clienteId) {
        return consulta.buscar(clienteId);
    }
}
