package com.maxiconecta.crm.comportamiento.evolucion;

import com.maxiconecta.crm.comportamiento.compra.Identificador;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Evolución del consumo de un cliente por categoría (SCRUM-32). El API Gateway exige
 * EVOLUCION_CONSUMO_CONSULTAR y audita el acceso con el {clienteId} de la ruta; igual que el
 * historial, la búsqueda real usa los identificadores del cliente en Marketplace y Ventas de la
 * query (docs/compra/evolucion-consumo-categoria.md).
 */
@RestController
@RequestMapping("/api/comportamiento/clientes")
public class EvolucionConsumoController {

    private final ConsultaEvolucionConsumo consulta;

    public EvolucionConsumoController(ConsultaEvolucionConsumo consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/{clienteId}/evolucion-consumo")
    public EvolucionConsumo evolucion(@PathVariable String clienteId,
                                      @RequestParam(name = "identificador", required = false) List<String> identificadores,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                      @RequestParam(required = false) String periodo,
                                      @RequestParam(required = false) String periodoBase,
                                      HttpServletRequest solicitud) {
        List<Identificador> ids = Identificador.parsearTodos(identificadores);
        FiltroEvolucionConsumo filtro = FiltroEvolucionConsumo.crear(desde, hasta, periodo,
                categorias(solicitud), periodoBase, consulta.hoy());
        return consulta.consultar(ids, filtro);
    }

    /**
     * Las categorías se leen sin la conversión de Spring, que partiría por las comas un único
     * {@code categoria=Hogar, jardín} y lo convertiría en dos categorías.
     */
    private static List<String> categorias(HttpServletRequest solicitud) {
        String[] valores = solicitud.getParameterValues("categoria");
        return valores == null ? List.of() : Arrays.asList(valores);
    }
}
