package com.maxiconecta.crm.comportamiento.compra;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Historial de compras de un cliente (SCRUM-194, SCRUM-16). El API Gateway exige el permiso
 * INDICADORES_CONSULTAR para /api/comportamiento/** y audita el acceso tomando {clienteId} de
 * la ruta (docs/seguridad/convencion-rutas-clientes.md). Este servicio no usa {clienteId} para
 * consultar -no conoce el perfil unificado-, sino los identificadores del cliente en Marketplace y
 * Ventas que llegan en la query
 * (docs/compra/historial-compras.md).
 */
@RestController
@RequestMapping("/api/comportamiento/clientes")
public class HistorialComprasController {

    private final ConsultaHistorialCompras consulta;

    public HistorialComprasController(ConsultaHistorialCompras consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/{clienteId}/compras")
    public HistorialCompras buscar(@PathVariable String clienteId,
                                   @RequestParam(name = "identificador", required = false) List<String> identificadores,
                                   @RequestParam(defaultValue = "0") int pagina,
                                   @RequestParam(defaultValue = "20") int tamanio) {
        Page<CompraResponse> resultado = consulta.buscar(Identificador.parsearTodos(identificadores), pagina, tamanio,
                CompraResponse::de);
        return new HistorialCompras(resultado.getContent(), resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements());
    }

    public record HistorialCompras(List<CompraResponse> content, int pagina, int tamanio, long total) {
    }

    public record CompraResponse(OffsetDateTime fecha, String referencia, BigDecimal montoTotal,
                                 String estado, List<ItemResponse> items) {

        static CompraResponse de(Compra compra) {
            return new CompraResponse(compra.getFecha(), compra.getIdCompraOrigen(),
                    compra.getMontoTotal(), compra.getEstado(), compra.getItems().stream().map(ItemResponse::de).toList());
        }
    }

    public record ItemResponse(String categoria, int cantidad, BigDecimal monto) {

        static ItemResponse de(CompraItem item) {
            return new ItemResponse(item.getCategoria(), item.getCantidad(), item.getMonto());
        }
    }
}
