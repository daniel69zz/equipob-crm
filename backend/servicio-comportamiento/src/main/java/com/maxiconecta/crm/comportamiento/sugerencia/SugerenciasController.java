package com.maxiconecta.crm.comportamiento.sugerencia;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCRUM-381: categorías afines sugeridas a un cliente. El API Gateway exige SUGERENCIAS_CONSULTAR
 * (SCRUM-383). No recibe categorías, montos ni miembros del segmento desde el navegador.
 */
@RestController
public class SugerenciasController {
    private final SugerenciasCategorias sugerencias;
    public SugerenciasController(SugerenciasCategorias sugerencias) { this.sugerencias = sugerencias; }

    @GetMapping("/api/comportamiento/clientes/{clienteId}/sugerencias-categorias")
    public SugerenciasCliente consultar(@PathVariable long clienteId) { return sugerencias.sugerir(clienteId); }
}
