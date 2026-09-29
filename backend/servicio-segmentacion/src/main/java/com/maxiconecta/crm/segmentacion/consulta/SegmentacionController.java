package com.maxiconecta.crm.segmentacion.consulta;

import com.maxiconecta.crm.segmentacion.segmento.SegmentoClienteRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta del segmento asignado a un cliente (SCRUM-149). El API Gateway exige
 * SEGMENTOS_CONSULTAR para GET /api/segmentacion/** y audita cada consulta
 * (docs/seguridad/convencion-rutas-clientes.md).
 */
@RestController
@RequestMapping("/api/segmentacion/clientes")
public class SegmentacionController {

    private final SegmentoClienteRepository segmentos;

    public SegmentacionController(SegmentoClienteRepository segmentos) {
        this.segmentos = segmentos;
    }

    @GetMapping("/{clienteId}/segmento")
    public SegmentoResponse obtener(@PathVariable Long clienteId) {
        return SegmentoResponse.de(segmentos.findById(clienteId));
    }
}
