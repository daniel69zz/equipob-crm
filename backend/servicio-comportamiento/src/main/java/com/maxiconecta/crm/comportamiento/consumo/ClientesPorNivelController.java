package com.maxiconecta.crm.comportamiento.consumo;

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
 * Listado de clientes por nivel de consumo (SCRUM-269, SCRUM-27). Igual que el de inactivos, un
 * listado filtrado usa un parámetro ({@code ?nivelConsumo=ALTO}) y no un segmento propio, para no
 * confundir el nivel con un identificador de cliente en la auditoría del Gateway
 * (docs/seguridad/convencion-rutas-clientes.md); ya aplica INDICADORES_CONSULTAR a todo
 * GET /api/comportamiento/**, sin cambios de permisos.
 */
@RestController
@RequestMapping("/api/comportamiento/clientes")
public class ClientesPorNivelController {

    private final ConsultaClientesPorNivel consulta;
    private final CriterioNivelConsumo criterio;

    public ClientesPorNivelController(ConsultaClientesPorNivel consulta, CriterioNivelConsumo criterio) {
        this.consulta = consulta;
        this.criterio = criterio;
    }

    @GetMapping(params = {"nivelConsumo", "!estado"})
    public ClientesPorNivel buscar(@RequestParam("nivelConsumo") String nivelConsumo,
                                   @RequestParam(defaultValue = "0") int pagina,
                                   @RequestParam(defaultValue = "20") int tamanio) {
        Page<ClienteNivelConsumo> resultado = consulta.buscar(parsear(nivelConsumo), pagina, tamanio);
        List<ClienteConNivel> contenido = resultado.getContent().stream().map(ClienteConNivel::de).toList();
        return new ClientesPorNivel(new Umbrales(criterio.umbralMedio(), criterio.umbralAlto()), contenido,
                resultado.getNumber(), resultado.getSize(), resultado.getTotalElements());
    }

    private static NivelConsumo parsear(String texto) {
        try {
            return NivelConsumo.valueOf(texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Nivel de consumo inválido: '" + texto + "' (se espera uno de "
                    + Arrays.toString(NivelConsumo.values()) + ")");
        }
    }

    public record ClientesPorNivel(Umbrales umbrales, List<ClienteConNivel> content, int pagina, int tamanio,
                                   long total) {
    }

    public record Umbrales(BigDecimal medio, BigDecimal alto) {
    }

    public record ClienteConNivel(String idClienteOrigen, NivelConsumo nivel, BigDecimal valorAcumulado,
                                  OffsetDateTime actualizadoEn) {

        static ClienteConNivel de(ClienteNivelConsumo cliente) {
            return new ClienteConNivel(cliente.getIdClienteOrigen(), cliente.getNivel(),
                    cliente.getValorAcumulado(), cliente.getActualizadoEn());
        }
    }
}
