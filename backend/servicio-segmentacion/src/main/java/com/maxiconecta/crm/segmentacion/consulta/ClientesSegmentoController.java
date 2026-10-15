package com.maxiconecta.crm.segmentacion.consulta;

import com.maxiconecta.crm.segmentacion.consulta.ListadoClientesRepository.SegmentoDisponible;
import com.maxiconecta.crm.segmentacion.consulta.OrdenClientes.Direccion;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Clientes de un segmento o nivel de consumo (SCRUM-277, SCRUM-28) y catálogo de segmentos para
 * elegirlo. Un listado filtrado usa parámetros ({@code ?segmento=Alto valor}) y no un segmento de
 * ruta propio, para que la auditoría del Gateway no lo confunda con un cliente
 * (docs/seguridad/convencion-rutas-clientes.md). El Gateway exige SEGMENTOS_CONSULTAR para
 * GET /api/segmentacion/** y reenvía la identidad del usuario en X-Usuario.
 */
@RestController
@RequestMapping("/api/segmentacion")
public class ClientesSegmentoController {

    private final ConsultaClientesSegmento consulta;

    public ClientesSegmentoController(ConsultaClientesSegmento consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/clientes")
    public ListadoClientes buscar(@RequestParam(required = false) String segmento,
                                  @RequestParam(required = false) String nivelConsumo,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                  LocalDate ultimaCompraDesde,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                  LocalDate ultimaCompraHasta,
                                  @RequestParam(required = false) String orden,
                                  @RequestParam(required = false) String direccion,
                                  @RequestParam(defaultValue = "0") int pagina,
                                  @RequestParam(defaultValue = "20") int tamanio,
                                  @RequestHeader("X-Usuario") String usuario) {
        return consulta.buscar(FiltroClientes.de(segmento, nivelConsumo, ultimaCompraDesde, ultimaCompraHasta),
                OrdenClientes.de(orden), Direccion.de(direccion), pagina, tamanio, usuario);
    }

    @GetMapping("/segmentos")
    public List<SegmentoDisponible> segmentos() {
        return consulta.segmentos();
    }
}
