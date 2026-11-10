package com.maxiconecta.crm.segmentacion.reporte;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Clientes por nivel de fidelización, pedidos a servicio-fidelizacion (SCRUM-464,
 * {@code POST /api/fidelizacion/reportes/distribucion-niveles}). Sin filtro se piden todos de una
 * vez; con filtro se manda el grupo de clientes en lotes, porque cada petición admite un máximo.
 * Si fidelización no responde, el reporte se entrega igual con los otros dos cortes y avisa de que
 * los niveles no están disponibles, como el listado por segmento ante servicio-perfil caído.
 */
@Component
public class NivelesFidelizacion {

    /** Tope por petición de {@code DistribucionNivelesController} en servicio-fidelizacion. */
    static final int TAMANIO_LOTE = 1000;

    private static final Logger log = LoggerFactory.getLogger(NivelesFidelizacion.class);
    private static final String RUTA = "/api/fidelizacion/reportes/distribucion-niveles";

    private final RestClient http;

    public NivelesFidelizacion(@Qualifier("fidelizacion") RestClient http) {
        this.http = http;
    }

    /** Niveles de todos los clientes con saldo de puntos (reporte sin filtro). */
    public ConteoNiveles todos() {
        return pedir(List.of(List.of()));
    }

    /** Niveles de un grupo de clientes (reporte filtrado), en lotes de {@link #TAMANIO_LOTE}. */
    public ConteoNiveles deClientes(List<Long> clientes) {
        if (clientes.isEmpty()) {
            return new ConteoNiveles(Map.of(), true);
        }
        List<List<Long>> lotes = new ArrayList<>();
        for (int desde = 0; desde < clientes.size(); desde += TAMANIO_LOTE) {
            lotes.add(clientes.subList(desde, Math.min(desde + TAMANIO_LOTE, clientes.size())));
        }
        return pedir(lotes);
    }

    private ConteoNiveles pedir(List<List<Long>> lotes) {
        Map<String, Long> porNivel = new LinkedHashMap<>();
        try {
            for (List<Long> lote : lotes) {
                Distribucion respuesta = http.post().uri(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new SolicitudNiveles(lote))
                        .retrieve().body(Distribucion.class);
                if (respuesta == null || respuesta.niveles() == null) {
                    continue;
                }
                for (Conteo conteo : respuesta.niveles()) {
                    // El nivel nulo (cliente con puntos pero sin nivel) se suma al grupo sin nivel del reporte.
                    if (conteo.nivel() != null && !conteo.nivel().isBlank()) {
                        porNivel.merge(conteo.nivel(), conteo.clientes(), Long::sum);
                    }
                }
            }
            return new ConteoNiveles(porNivel, true);
        } catch (RestClientException ex) {
            log.warn("No se pudo consultar la distribución de niveles de fidelización; el reporte se entregará "
                    + "sin ese corte ({})", ex.getClass().getSimpleName());
            return new ConteoNiveles(Map.of(), false);
        }
    }

    /**
     * Clientes por nivel con nombre, sin el grupo sin nivel: ese lo calcula el reporte con su propia
     * población. {@code disponibles} es falso cuando servicio-fidelizacion no respondió.
     */
    public record ConteoNiveles(Map<String, Long> porNivel, boolean disponibles) {
    }

    private record SolicitudNiveles(List<Long> clientes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Distribucion(long total, List<Conteo> niveles) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Conteo(String nivel, long clientes) {
    }
}
