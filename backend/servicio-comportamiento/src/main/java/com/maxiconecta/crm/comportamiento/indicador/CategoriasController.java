package com.maxiconecta.crm.comportamiento.indicador;

import com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException;
import com.maxiconecta.crm.comportamiento.compra.Identificador;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Categorías más consumidas por un cliente (SCRUM-18). Igual que el historial y el ticket promedio,
 * el API Gateway exige INDICADORES_CONSULTAR y audita el acceso con el {clienteId} de la ruta; la
 * búsqueda real usa los identificadores por canal de la query
 * (docs/compra/categorias-mas-consumidas.md).
 */
@RestController
@RequestMapping("/api/comportamiento/clientes")
public class CategoriasController {

    private final ConsultaIndicadores consulta;

    public CategoriasController(ConsultaIndicadores consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/{clienteId}/categorias")
    public CategoriasConsumidas categorias(@PathVariable String clienteId,
                                           @RequestParam(name = "identificador", required = false) List<String> identificadores,
                                           @RequestParam(required = false) Integer limite) {
        if (limite != null && limite < 1) {
            throw new ReglaNegocioException("El límite debe ser mayor o igual que 1");
        }
        return new CategoriasConsumidas(
                consulta.categoriasMasConsumidas(Identificador.parsearTodos(identificadores), limite));
    }

    /** De mayor a menor consumo; sin compras vigentes la lista viene vacía. */
    public record CategoriasConsumidas(List<CategoriaConsumida> categorias) {
    }
}
