package com.maxiconecta.crm.perfil.consulta;

import com.maxiconecta.crm.perfil.cliente.ClienteOrigen;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * SCRUM-151 · Historial de compras del cliente, consultado en servicio-comportamiento para la
 * ficha integral.
 */
class HistorialComprasClienteTest {

    private static final List<ClienteOrigen> UN_IDENTIFICADOR = List.of(
            new ClienteOrigen("mp-user-1", 42L, OffsetDateTime.now()));

    @Test
    void devuelveLasComprasQueRespondeComportamiento() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://comportamiento.test");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        HistorialComprasCliente historial = new HistorialComprasCliente(builder.build());

        servidor.expect(requestTo(
                        "http://comportamiento.test/api/comportamiento/clientes/42/compras?tamanio=100&identificador=mp-user-1"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"content":[{"fecha":"2026-09-01T10:00:00Z","referencia":"C-1","montoTotal":150.00,
                        "estado":"CONFIRMADA","items":[{"categoria":"ROPA","cantidad":1,"monto":150.00}]}],
                        "pagina":0,"tamanio":100,"total":1}
                        """, MediaType.APPLICATION_JSON));

        List<CompraResponse> compras = historial.buscar(42L, UN_IDENTIFICADOR);

        assertThat(compras).hasSize(1);
        assertThat(compras.get(0).referencia()).isEqualTo("C-1");
    }

    @Test
    void sinIdentificadoresDeOrigenNoConsultaYDevuelveListaVacia() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://comportamiento.test");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        HistorialComprasCliente historial = new HistorialComprasCliente(builder.build());

        List<CompraResponse> compras = historial.buscar(42L, List.of());

        assertThat(compras).isEmpty();
        servidor.verify();
    }

    @Test
    void siComportamientoNoRespondeLaFichaSigueCargandoSinEseBloque() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://comportamiento.test");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        HistorialComprasCliente historial = new HistorialComprasCliente(builder.build());

        servidor.expect(requestTo(
                        "http://comportamiento.test/api/comportamiento/clientes/42/compras?tamanio=100&identificador=mp-user-1"))
                .andRespond(withServerError());

        List<CompraResponse> compras = historial.buscar(42L, UN_IDENTIFICADOR);

        assertThat(compras).isEmpty();
    }
}
