package com.maxiconecta.crm.comportamiento.riesgo;

import com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Listado de clientes por nivel de riesgo de abandono (SCRUM-326, SCRUM-35): con
 * {@code ?riesgoAbandono=ALTO} quedan identificados los clientes a retener (criterio de aceptación 3).
 * Como los listados de inactivos y de nivel de consumo, usa un parámetro y no un segmento de ruta
 * propio (docs/seguridad/convencion-rutas-clientes.md); el Gateway ya exige INDICADORES_CONSULTAR a
 * todo GET /api/comportamiento/**, sin cambios de permisos.
 */
@RestController
@RequestMapping("/api/comportamiento/clientes")
public class ClientesEnRiesgoController {

    private final ConsultaClientesEnRiesgo consulta;
    private final CriterioRiesgoAbandono criterio;

    public ClientesEnRiesgoController(ConsultaClientesEnRiesgo consulta, CriterioRiesgoAbandono criterio) {
        this.consulta = consulta;
        this.criterio = criterio;
    }

    @GetMapping(params = {"riesgoAbandono", "!estado", "!nivelConsumo"})
    public ClientesEnRiesgo buscar(@RequestParam("riesgoAbandono") String riesgoAbandono,
                                   @RequestParam(defaultValue = "0") int pagina,
                                   @RequestParam(defaultValue = "20") int tamanio) {
        Page<RiesgoAbandonoCliente> resultado = consulta.buscar(parsear(riesgoAbandono), pagina, tamanio);
        List<ClienteEnRiesgo> contenido = resultado.getContent().stream().map(ClienteEnRiesgo::de).toList();
        return new ClientesEnRiesgo(new Umbrales(criterio.umbralMedio(), criterio.umbralAlto()), contenido,
                resultado.getNumber(), resultado.getSize(), resultado.getTotalElements());
    }

    private static NivelRiesgo parsear(String texto) {
        try {
            return NivelRiesgo.valueOf(texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Nivel de riesgo de abandono inválido: '" + texto + "' (se espera uno de "
                    + Arrays.toString(NivelRiesgo.values()) + ")");
        }
    }

    public record ClientesEnRiesgo(Umbrales umbrales, List<ClienteEnRiesgo> content, int pagina, int tamanio,
                                   long total) {
    }

    public record Umbrales(BigDecimal medio, BigDecimal alto) {
    }

    public record ClienteEnRiesgo(Long clienteId, NivelRiesgo nivel, BigDecimal puntaje, int diasSinComprar,
                                  OffsetDateTime ultimaCompra, boolean inactivo, boolean cambioDetectado,
                                  OffsetDateTime enSeguimientoDesde, OffsetDateTime calculadoEn) {

        static ClienteEnRiesgo de(RiesgoAbandonoCliente riesgo) {
            return new ClienteEnRiesgo(riesgo.getClienteId(), riesgo.getNivel(), riesgo.getPuntaje(),
                    riesgo.getDiasSinComprar(), riesgo.getUltimaCompra(), riesgo.isInactivo(),
                    riesgo.isCambioDetectado(), riesgo.getEnSeguimientoDesde(), riesgo.getCalculadoEn());
        }
    }
}
