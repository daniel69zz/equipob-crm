package com.maxiconecta.crm.fidelizacion.reporte;

import com.maxiconecta.crm.fidelizacion.puntos.SaldoPuntosRepository;
import com.maxiconecta.crm.fidelizacion.puntos.SaldoPuntosRepository.ConteoNivel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cuántos clientes hay en cada nivel de fidelización (SCRUM-464), para el reporte de distribución de
 * clientes que arma servicio-segmentacion (SCRUM-57,
 * docs/segmentacion/reportes-distribucion.md). Es una consulta interna entre servicios, como
 * {@code GET /api/segmentacion/segmentos/miembros}: no lleva la identidad del Gateway y el Gateway
 * deniega {@code /api/fidelizacion/reportes/**} para que ningún usuario la alcance por ahí. El
 * permiso del reporte se comprueba en su propia ruta ({@code REPORTES_CONSULTAR}), porque el Agente
 * de Atención al Cliente tiene {@code PUNTOS_CONSULTAR} y el reporte no es para él.
 *
 * <p>Es POST y no GET porque el reporte filtrado manda el grupo de clientes en el cuerpo: son
 * demasiados para la línea de consulta. No modifica nada.
 */
@RestController
@RequestMapping("/api/fidelizacion/reportes")
public class DistribucionNivelesController {

    /** Tope por petición; servicio-segmentacion parte el grupo en trozos de este tamaño. */
    static final int MAXIMO_CLIENTES = 1000;

    private final SaldoPuntosRepository saldos;

    public DistribucionNivelesController(SaldoPuntosRepository saldos) {
        this.saldos = saldos;
    }

    /**
     * @param solicitud grupo de clientes a contar; sin cuerpo o con la lista vacía cuenta a todos los
     *                  clientes con saldo de puntos
     */
    @PostMapping("/distribucion-niveles")
    public DistribucionNiveles distribucion(@RequestBody(required = false) SolicitudDistribucion solicitud) {
        Set<Long> clientes = solicitud == null ? Set.of() : solicitud.clientesValidos();
        List<ConteoNivel> conteos = clientes.isEmpty() ? saldos.contarPorNivel() : saldos.contarPorNivel(clientes);
        List<ConteoNivel> ordenados = conteos.stream()
                .sorted(DistribucionNiveles.POR_NIVEL)
                .toList();
        long total = ordenados.stream().mapToLong(ConteoNivel::getClientes).sum();
        return new DistribucionNiveles(total, ordenados.stream()
                .map(conteo -> new DistribucionNiveles.Conteo(conteo.getNivel(), conteo.getClientes()))
                .toList());
    }

    /** Clientes del reporte filtrado; {@code null} o vacío significa "todos". */
    public record SolicitudDistribucion(List<Long> clientes) {

        Set<Long> clientesValidos() {
            if (clientes == null || clientes.isEmpty()) {
                return Set.of();
            }
            if (clientes.size() > MAXIMO_CLIENTES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El grupo no puede tener más de "
                        + MAXIMO_CLIENTES + " clientes por petición");
            }
            Set<Long> sinRepetidos = clientes.stream().filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (sinRepetidos.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La lista de clientes no puede tener solo valores nulos");
            }
            return sinRepetidos;
        }
    }
}
