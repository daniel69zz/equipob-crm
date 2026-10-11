package com.maxiconecta.crm.comportamiento.periodo;

import com.maxiconecta.crm.comportamiento.compra.Identificador;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Valor total y promedio que consumió un cliente en un periodo (SCRUM-39). Igual que el historial y
 * los indicadores, el API Gateway exige INDICADORES_CONSULTAR para todo GET /api/comportamiento/**
 * y audita el acceso con el {clienteId} de la ruta (SCRUM-348); la búsqueda real usa los
 * identificadores del cliente en Marketplace y Ventas de la query
 * (docs/compra/consumo-por-periodo.md).
 */
@RestController
@RequestMapping("/api/comportamiento/clientes")
public class ValorPorPeriodoController {

    private final ConsultaValorPorPeriodo consulta;

    public ValorPorPeriodoController(ConsultaValorPorPeriodo consulta) {
        this.consulta = consulta;
    }

    /**
     * {@code desde} y {@code hasta} son obligatorias; si faltan o están al revés, la
     * {@link com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException} de {@link RangoPeriodo}
     * responde 400.
     */
    @GetMapping("/{clienteId}/consumo-periodo")
    public ValorPorPeriodo consumoPorPeriodo(@PathVariable String clienteId,
                                             @RequestParam(name = "identificador", required = false) List<String> identificadores,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return consulta.consultar(Identificador.parsearTodos(identificadores), desde, hasta);
    }
}
