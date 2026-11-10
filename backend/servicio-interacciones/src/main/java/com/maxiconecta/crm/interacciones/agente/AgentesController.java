package com.maxiconecta.crm.interacciones.agente;

import com.maxiconecta.crm.interacciones.comun.IdentidadGateway;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code GET /api/interacciones/agentes} (SCRUM-457): los agentes activos para elegir el responsable de una
 * interacción. No es una ruta de cliente (docs/seguridad/convencion-rutas-clientes.md, regla 5).
 */
@RestController
public class AgentesController {

    private final AgentesAtencion agentes;

    public AgentesController(AgentesAtencion agentes) {
        this.agentes = agentes;
    }

    @GetMapping("/api/interacciones/agentes")
    public List<AgentesAtencion.Agente> activos(HttpServletRequest http) {
        IdentidadGateway.registro(http);
        return agentes.activos();
    }
}
