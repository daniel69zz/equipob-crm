package com.maxiconecta.crm.comportamiento.cambio;

import com.maxiconecta.crm.comportamiento.compra.Identificador;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Alertas de cambio de comportamiento de un cliente (SCRUM-315, SCRUM-34). Igual que los
 * indicadores, el API Gateway exige INDICADORES_CONSULTAR y audita el acceso con el {clienteId} de
 * la ruta; la búsqueda real usa los identificadores del cliente en Marketplace y Ventas de la query.
 */
@RestController
@RequestMapping("/api/comportamiento/clientes")
public class CambiosComportamientoController {

    private final ConsultaCambiosComportamiento consulta;

    public CambiosComportamientoController(ConsultaCambiosComportamiento consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/{clienteId}/cambios-comportamiento")
    public ConsultaCambiosComportamiento.CambiosComportamiento buscar(
            @PathVariable String clienteId,
            @RequestParam(name = "identificador", required = false) List<String> identificadores) {
        return consulta.buscar(Identificador.parsearTodos(identificadores));
    }
}
