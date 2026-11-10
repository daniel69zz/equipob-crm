package com.maxiconecta.crm.interacciones.compras;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Compras del cliente en servicio-comportamiento ({@code GET /api/comportamiento/clientes/{clienteId}/compras}),
 * buscadas por sus identificadores en Marketplace y Ventas, como hace la ficha integral (SCRUM-151). Si el servicio
 * no responde, el historial no falla (SCRUM-441). También da las compras que el agente puede vincular a una
 * interacción y comprueba que la elegida sea del cliente (SCRUM-446).
 */
@Component
public class ComprasCliente {

    private static final Logger log = LoggerFactory.getLogger(ComprasCliente.class);

    /** El tamaño de página más grande que acepta servicio-comportamiento. */
    static final int TAMANIO_PAGINA = 100;
    /** Se buscan compras entre las 500 más recientes del cliente (docs/interacciones/vinculacion-compras.md). */
    static final int MAXIMO_PAGINAS = 5;

    private final RestClient http;

    public ComprasCliente(@Qualifier("comportamiento") RestClient http) {
        this.http = http;
    }

    /** Las {@code limite} compras más recientes; se llama solo con identificadores. */
    public ComprasRecientes recientes(long clienteId, List<String> identificadores, int limite) {
        try {
            Pagina pagina = pagina(clienteId, identificadores, 0, limite);
            if (pagina == null || pagina.content() == null) {
                return ComprasRecientes.ninguna();
            }
            return new ComprasRecientes(pagina.content(), Math.max(pagina.total(), pagina.content().size()), true);
        } catch (RestClientException ex) {
            log.warn("No se pudieron consultar las compras del cliente {}; el historial seguirá sin ellas ({})",
                    clienteId, ex.getClass().getSimpleName());
            return ComprasRecientes.noDisponibles();
        }
    }

    /**
     * Las compras que el agente puede elegir para vincular (SCRUM-446): hasta {@code limite}, de la más reciente a la
     * más antigua, cuya referencia contiene {@code texto} sin distinguir mayúsculas (todas si es nulo). Si
     * Comportamiento no responde, no están disponibles.
     */
    public ComprasRecientes elegibles(long clienteId, List<String> identificadores, String texto, int limite) {
        String buscado = texto == null ? null : texto.toLowerCase(Locale.ROOT);
        List<Compra> encontradas = new ArrayList<>();
        try {
            recorrer(clienteId, identificadores, compra -> {
                if (buscado == null || (compra.referencia() != null
                        && compra.referencia().toLowerCase(Locale.ROOT).contains(buscado))) {
                    encontradas.add(compra);
                }
                return encontradas.size() >= limite;
            });
            return new ComprasRecientes(List.copyOf(encontradas), encontradas.size(), true);
        } catch (RestClientException ex) {
            log.warn("No se pudieron consultar las compras del cliente {} para vincularlas ({})", clienteId,
                    ex.getClass().getSimpleName());
            return ComprasRecientes.noDisponibles();
        }
    }

    /**
     * @return la compra del cliente con esa referencia, o vacío si no es suya (criterio de aceptación 2 de SCRUM-54)
     * @throws ResponseStatusException 503 si Comportamiento no responde: sin él no se puede comprobar la compra
     */
    public Optional<Compra> buscar(long clienteId, List<String> identificadores, String referencia) {
        List<Compra> hallada = new ArrayList<>(1);
        try {
            recorrer(clienteId, identificadores, compra -> {
                if (referencia.equals(compra.referencia())) {
                    hallada.add(compra);
                    return true;
                }
                return false;
            });
        } catch (RestClientException ex) {
            log.warn("No se pudo comprobar la compra {} del cliente {} ({})", referencia, clienteId,
                    ex.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudieron consultar las compras del cliente; intente de nuevo en unos minutos");
        }
        return hallada.stream().findFirst();
    }

    /** Recorre las compras de la más reciente a la más antigua, hasta que {@code detener} lo pida o se acaben. */
    private void recorrer(long clienteId, List<String> identificadores, Predicate<Compra> detener) {
        for (int numero = 0; numero < MAXIMO_PAGINAS; numero++) {
            Pagina pagina = pagina(clienteId, identificadores, numero, TAMANIO_PAGINA);
            if (pagina == null || pagina.content() == null || pagina.content().isEmpty()) {
                return;
            }
            for (Compra compra : pagina.content()) {
                if (detener.test(compra)) {
                    return;
                }
            }
            if ((long) (numero + 1) * TAMANIO_PAGINA >= pagina.total()) {
                return;
            }
        }
    }

    private Pagina pagina(long clienteId, List<String> identificadores, int numero, int tamanio) {
        return http.get()
                .uri(builder -> {
                    builder.path("/api/comportamiento/clientes/{clienteId}/compras")
                            .queryParam("pagina", numero).queryParam("tamanio", tamanio);
                    identificadores.forEach(id -> builder.queryParam("identificador", id));
                    return builder.build(clienteId);
                })
                .retrieve().body(Pagina.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Pagina(List<Compra> content, long total) {
    }
}
