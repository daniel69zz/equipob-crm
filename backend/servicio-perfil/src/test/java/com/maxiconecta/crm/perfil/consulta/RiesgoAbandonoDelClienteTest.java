package com.maxiconecta.crm.perfil.consulta;

import com.maxiconecta.crm.perfil.cliente.ClienteOrigen;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
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
 * SCRUM-323 · Riesgo de abandono del cliente, consultado en servicio-comportamiento para la ficha
 * integral (SCRUM-325).
 */
class RiesgoAbandonoDelClienteTest {

    private static final String RUTA = "http://comportamiento.test/api/comportamiento/clientes/42/riesgo-abandono";
    private static final List<ClienteOrigen> UN_IDENTIFICADOR = List.of(
            new ClienteOrigen("mp-user-1", 42L, OffsetDateTime.now()));

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://comportamiento.test");
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
    private final RiesgoAbandonoDelCliente riesgoAbandono = new RiesgoAbandonoDelCliente(builder.build());

    @Test
    void devuelveElNivelYLosFactoresQueCalculaComportamiento() {
        servidor.expect(requestTo(RUTA))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"clienteId":42,"nivel":"ALTO","puntaje":67.78,"inactivo":false,
                         "enSeguimientoDesde":"2026-10-20T08:30:00Z","calculadoEn":"2026-10-20T08:30:00Z",
                         "factores":[
                           {"factor":"RECENCIA","puntos":27.78,"maximo":50,"descripcion":"50 días sin comprar"},
                           {"factor":"CAMBIO_COMPORTAMIENTO","puntos":15,"maximo":15,"descripcion":"Bajó su frecuencia"}],
                         "sinDatos":false}
                        """, MediaType.APPLICATION_JSON));

        RiesgoAbandonoResponse respuesta = riesgoAbandono.buscar(42L, UN_IDENTIFICADOR);

        assertThat(respuesta.nivel()).isEqualTo("ALTO");
        assertThat(respuesta.puntaje()).isEqualByComparingTo("67.78");
        assertThat(respuesta.enSeguimientoDesde()).isEqualTo("2026-10-20T08:30:00Z");
        assertThat(respuesta.factores()).extracting(RiesgoAbandonoResponse.Factor::factor)
                .containsExactly("RECENCIA", "CAMBIO_COMPORTAMIENTO");
        assertThat(respuesta.factores().get(0).maximo()).isEqualByComparingTo("50");
        assertThat(respuesta.sinDatos()).isFalse();
    }

    @Test
    void unClienteSinRiesgoCalculadoQuedaSinDatos() {
        servidor.expect(requestTo(RUTA))
                .andRespond(withSuccess("""
                        {"clienteId":42,"nivel":null,"puntaje":null,"factores":[],"sinDatos":true}
                        """, MediaType.APPLICATION_JSON));

        RiesgoAbandonoResponse respuesta = riesgoAbandono.buscar(42L, UN_IDENTIFICADOR);

        assertThat(respuesta.sinDatos()).isTrue();
        assertThat(respuesta.nivel()).isNull();
    }

    @Test
    void sinIdentificadoresDeOrigenNoConsultaYQuedaSinDatos() {
        RiesgoAbandonoResponse respuesta = riesgoAbandono.buscar(42L, List.of());

        assertThat(respuesta.sinDatos()).isTrue();
        servidor.verify();
    }

    @Test
    void siComportamientoNoRespondeLaFichaSigueCargandoSinEseBloque() {
        servidor.expect(requestTo(RUTA)).andRespond(withServerError());

        RiesgoAbandonoResponse respuesta = riesgoAbandono.buscar(42L, UN_IDENTIFICADOR);

        assertThat(respuesta.sinDatos()).isTrue();
    }
}
