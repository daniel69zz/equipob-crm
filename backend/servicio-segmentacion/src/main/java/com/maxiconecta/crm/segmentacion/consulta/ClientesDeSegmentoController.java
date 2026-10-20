package com.maxiconecta.crm.segmentacion.consulta;

import com.maxiconecta.crm.segmentacion.segmento.SegmentoCliente;
import com.maxiconecta.crm.segmentacion.segmento.SegmentoClienteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Clientes asignados a un segmento (SCRUM-357), que servicio-comportamiento consulta para agregar
 * solo a los clientes de un segmento al comparar periodos (SCRUM-40,
 * docs/compra/comparacion-periodos.md). Es una consulta interna entre servicios: solo devuelve los
 * identificadores. El listado para usuarios, con nombres, filtros y auditoría, es
 * {@code GET /api/segmentacion/clientes} (SCRUM-28, {@link ClientesSegmentoController}); por eso esta
 * ruta es distinta. El API Gateway exige SEGMENTOS_CONSULTAR para GET /api/segmentacion/**.
 */
@RestController
@RequestMapping("/api/segmentacion/segmentos/miembros")
public class ClientesDeSegmentoController {

    static final int LARGO_MAXIMO_SEGMENTO = 60;
    static final int TAMANIO_MAXIMO_PAGINA = 1000;

    private final SegmentoClienteRepository segmentos;

    public ClientesDeSegmentoController(SegmentoClienteRepository segmentos) {
        this.segmentos = segmentos;
    }

    @GetMapping
    public ClientesDeSegmento buscar(@RequestParam("segmento") String segmento,
                                     @RequestParam(defaultValue = "0") int pagina,
                                     @RequestParam(defaultValue = "100") int tamanio) {
        String codigo = segmento.strip();
        if (codigo.isEmpty() || codigo.length() > LARGO_MAXIMO_SEGMENTO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El segmento debe tener entre 1 y "
                    + LARGO_MAXIMO_SEGMENTO + " caracteres");
        }
        PageRequest solicitud = PageRequest.of(Math.max(pagina, 0),
                Math.min(Math.max(tamanio, 1), TAMANIO_MAXIMO_PAGINA));
        Page<SegmentoCliente> resultado = segmentos.findBySegmentoOrderByClienteId(codigo, solicitud);
        List<ClienteDeSegmento> contenido = resultado.getContent().stream()
                .map(cliente -> new ClienteDeSegmento(cliente.getClienteId(), cliente.getAsignadoEn()))
                .toList();
        return new ClientesDeSegmento(codigo, contenido, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements());
    }

    public record ClientesDeSegmento(String segmento, List<ClienteDeSegmento> content, int pagina, int tamanio,
                                     long total) {
    }

    public record ClienteDeSegmento(Long clienteId, OffsetDateTime asignadoEn) {
    }
}
