package com.maxiconecta.crm.comportamiento.comparacion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException;
import com.maxiconecta.crm.comportamiento.comun.ServicioNoDisponibleException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Clientes asignados a un segmento, consultados en servicio-segmentacion
 * (GET /api/segmentacion/clientes?segmento=, SCRUM-357) para comparar periodos solo con los clientes
 * de ese segmento (docs/compra/comparacion-periodos.md). Devuelve los identificadores de perfil, que
 * son los que guarda cada compra vinculada ({@code compra.id_cliente}).
 */
@Component
public class ClientesDelSegmento {

    static final int TAMANIO_PAGINA = 1000;
    /** Tope de clientes de un segmento que se comparan de una vez: 100.000. */
    static final int PAGINAS_MAXIMAS = 100;

    private final RestClient http;

    @Autowired
    public ClientesDelSegmento(RestClient.Builder builder,
                               @Value("${crm.segmentacion.url}") String url,
                               @Value("${crm.segmentacion.tiempo-espera}") Duration tiempoEspera) {
        HttpClient cliente = HttpClient.newBuilder().connectTimeout(tiempoEspera).build();
        JdkClientHttpRequestFactory solicitudes = new JdkClientHttpRequestFactory(cliente);
        solicitudes.setReadTimeout(tiempoEspera);
        this.http = builder.baseUrl(url).requestFactory(solicitudes).build();
    }

    /** Constructor para pruebas, con el cliente HTTP ya armado. */
    ClientesDelSegmento(RestClient http) {
        this.http = http;
    }

    public List<Long> idsDe(String segmento) {
        List<Long> ids = new ArrayList<>();
        try {
            for (int pagina = 0; pagina < PAGINAS_MAXIMAS; pagina++) {
                final int numero = pagina;
                PaginaClientes respuesta = http.get()
                        .uri(builder -> builder.path("/api/segmentacion/clientes")
                                .queryParam("segmento", "{segmento}")
                                .queryParam("pagina", numero)
                                .queryParam("tamanio", TAMANIO_PAGINA)
                                .build(segmento))
                        .retrieve().body(PaginaClientes.class);
                if (respuesta == null || respuesta.content() == null || respuesta.content().isEmpty()) {
                    return ids;
                }
                respuesta.content().forEach(cliente -> ids.add(cliente.clienteId()));
                if (ids.size() >= respuesta.total()) {
                    return ids;
                }
            }
        } catch (RestClientException ex) {
            throw new ServicioNoDisponibleException("No se pudo consultar el segmento '" + segmento
                    + "' en el servicio de segmentación; intente de nuevo más tarde", ex);
        }
        throw new ReglaNegocioException("El segmento '" + segmento + "' tiene más de "
                + PAGINAS_MAXIMAS * TAMANIO_PAGINA + " clientes y no se puede comparar de una vez");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaginaClientes(List<ClienteSegmento> content, long total) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ClienteSegmento(Long clienteId) {
    }
}
