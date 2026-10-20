package com.maxiconecta.crm.comportamiento.comparacion;

import com.maxiconecta.crm.comportamiento.comun.ServicioNoDisponibleException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * SCRUM-357, SCRUM-358 · Clientes de un segmento, consultados en servicio-segmentacion, sin red real.
 */
class ClientesDelSegmentoTest {

    private static final String BASE = "http://segmentacion.test/api/segmentacion/segmentos/miembros?segmento=FRECUENTE";

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://segmentacion.test");
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
    private final ClientesDelSegmento clientes = new ClientesDelSegmento(builder.build());

    @Test
    void devuelveLosIdentificadoresDePerfilDeLosClientesDelSegmento() {
        servidor.expect(requestTo(BASE + "&pagina=0&tamanio=1000"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"segmento":"FRECUENTE","content":[{"clienteId":7,"asignadoEn":"2026-09-01T10:00:00Z"},
                        {"clienteId":42,"asignadoEn":"2026-09-02T10:00:00Z"}],"pagina":0,"tamanio":1000,"total":2}
                        """, MediaType.APPLICATION_JSON));

        assertThat(clientes.idsDe("FRECUENTE")).containsExactly(7L, 42L);
        servidor.verify();
    }

    @Test
    void recorreTodasLasPaginasMientrasFaltenClientes() {
        servidor.expect(requestTo(BASE + "&pagina=0&tamanio=1000"))
                .andRespond(withSuccess("""
                        {"content":[{"clienteId":1},{"clienteId":2}],"pagina":0,"tamanio":1000,"total":3}
                        """, MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + "&pagina=1&tamanio=1000"))
                .andRespond(withSuccess("""
                        {"content":[{"clienteId":3}],"pagina":1,"tamanio":1000,"total":3}
                        """, MediaType.APPLICATION_JSON));

        assertThat(clientes.idsDe("FRECUENTE")).containsExactly(1L, 2L, 3L);
        servidor.verify();
    }

    @Test
    void unSegmentoSinClientesDevuelveUnaListaVacia() {
        servidor.expect(requestTo(BASE + "&pagina=0&tamanio=1000"))
                .andRespond(withSuccess("""
                        {"content":[],"pagina":0,"tamanio":1000,"total":0}
                        """, MediaType.APPLICATION_JSON));

        assertThat(clientes.idsDe("FRECUENTE")).isEqualTo(List.of());
    }

    @Test
    void siSegmentacionNoRespondeSeAvisaConUnaExcepcionDeServicioNoDisponible() {
        servidor.expect(requestTo(BASE + "&pagina=0&tamanio=1000")).andRespond(withServerError());

        assertThatThrownBy(() -> clientes.idsDe("FRECUENTE"))
                .isInstanceOf(ServicioNoDisponibleException.class)
                .hasMessageContaining("FRECUENTE");
    }
}
