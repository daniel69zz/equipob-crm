package com.maxiconecta.crm.comportamiento.indicador;

import com.maxiconecta.crm.comportamiento.compra.Identificador;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Indicadores de comportamiento de un cliente (SCRUM-17, SCRUM-19, SCRUM-37). Igual que el historial de compras, el
 * API Gateway exige INDICADORES_CONSULTAR y audita el acceso con el {clienteId} de la ruta; la
 * búsqueda real usa los identificadores del cliente en Marketplace y Ventas de la query (docs/compra/ticket-promedio.md).
 */
@RestController
@RequestMapping("/api/comportamiento/clientes")
public class IndicadoresController {

    private final ConsultaIndicadores consulta;

    public IndicadoresController(ConsultaIndicadores consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/{clienteId}/indicadores")
    public IndicadoresCliente indicadores(@PathVariable String clienteId,
                                          @RequestParam(name = "identificador", required = false) List<String> identificadores) {
        List<Identificador> ids = Identificador.parsearTodos(identificadores);
        return new IndicadoresCliente(consulta.ticketPromedio(ids), consulta.recencia(ids), consulta.frecuencia(ids),
                consulta.valorAcumulado(ids));
    }

    /** Cada indicador es un campo propio, para que los nuevos se sumen sin cambiar los existentes. */
    public record IndicadoresCliente(TicketPromedio ticketPromedio, RecenciaCompra recencia, long frecuencia,
                                     ValorAcumulado valorAcumulado) {
    }
}
