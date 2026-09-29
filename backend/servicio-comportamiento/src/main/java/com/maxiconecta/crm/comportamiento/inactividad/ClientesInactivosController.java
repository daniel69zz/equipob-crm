package com.maxiconecta.crm.comportamiento.inactividad;

import com.maxiconecta.crm.comportamiento.compra.Origen;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Listado de clientes inactivos (SCRUM-298, SCRUM-31): sin compras vigentes desde hace más del
 * umbral configurado (docs/compra/clientes-inactivos.md). Ruta bajo /clientes según
 * docs/seguridad/convencion-rutas-clientes.md: un listado filtrado usa {@code ?estado=inactivo}
 * en vez de un segmento propio, para no confundir "inactivo" con un identificador de cliente en
 * la auditoría del Gateway (que exige INDICADORES_CONSULTAR para todo GET /api/comportamiento/**,
 * sin cambios de permisos).
 */
@RestController
@RequestMapping("/api/comportamiento/clientes")
public class ClientesInactivosController {

    private final ConsultaClientesInactivos consulta;
    private final CriterioInactividad criterio;
    private final Clock reloj;

    public ClientesInactivosController(ConsultaClientesInactivos consulta, CriterioInactividad criterio, Clock reloj) {
        this.consulta = consulta;
        this.criterio = criterio;
        this.reloj = reloj;
    }

    @GetMapping(params = "estado=inactivo")
    public ClientesInactivos buscar(@RequestParam(defaultValue = "0") int pagina,
                                    @RequestParam(defaultValue = "20") int tamanio) {
        Page<ClienteInactivo> resultado = consulta.buscar(pagina, tamanio);
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        List<ClienteInactivoResponse> contenido = resultado.getContent().stream()
                .map(cliente -> ClienteInactivoResponse.de(cliente, ahora))
                .toList();
        return new ClientesInactivos(criterio.umbralDias(), contenido, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements());
    }

    public record ClientesInactivos(int umbralDias, List<ClienteInactivoResponse> content, int pagina, int tamanio,
                                    long total) {
    }

    public record ClienteInactivoResponse(Origen origen, String idClienteOrigen, OffsetDateTime ultimaCompra,
                                          long diasTranscurridos) {

        static ClienteInactivoResponse de(ClienteInactivo cliente, OffsetDateTime ahora) {
            long dias = ChronoUnit.DAYS.between(cliente.getUltimaCompra(), ahora);
            return new ClienteInactivoResponse(cliente.getOrigen(), cliente.getIdClienteOrigen(),
                    cliente.getUltimaCompra(), dias);
        }
    }
}
