package com.maxiconecta.crm.comportamiento.comparacion;

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
 * Comparación de la frecuencia y el valor de compra entre dos periodos (SCRUM-40,
 * docs/compra/comparacion-periodos.md), para un cliente o para un segmento.
 * <p>
 * La del cliente cuelga de {@code /clientes/{clienteId}} para que el API Gateway la audite con ese
 * identificador (exige INDICADORES_CONSULTAR, como el resto de los indicadores); la búsqueda real usa
 * los {@code identificador} de la query. La del segmento devuelve solo agregados, sin datos de
 * clientes, así que va fuera de {@code /clientes} (docs/seguridad/convencion-rutas-clientes.md) y el
 * Gateway exige además SEGMENTOS_CONSULTAR (SCRUM-356).
 */
@RestController
@RequestMapping("/api/comportamiento")
public class ComparacionPeriodosController {

    private final ConsultaComparacionPeriodos consulta;

    public ComparacionPeriodosController(ConsultaComparacionPeriodos consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/clientes/{clienteId}/comparacion-periodos")
    public ResultadoComparacion compararCliente(@PathVariable String clienteId,
                                                @RequestParam(name = "identificador", required = false) List<String> identificadores,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desdeA,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hastaA,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desdeB,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hastaB) {
        PeriodosAComparar periodos = PeriodosAComparar.de(desdeA, hastaA, desdeB, hastaB);
        return consulta.compararCliente(Identificador.parsearTodos(identificadores), periodos);
    }

    @GetMapping("/comparacion-periodos")
    public ResultadoComparacion compararSegmento(@RequestParam(required = false) String segmento,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desdeA,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hastaA,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desdeB,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hastaB) {
        PeriodosAComparar periodos = PeriodosAComparar.de(desdeA, hastaA, desdeB, hastaB);
        return consulta.compararSegmento(segmento, periodos);
    }
}
