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
 * SCRUM-566 · Nivel de consumo del cliente, consultado en servicio-comportamiento para la ficha
 * integral (SCRUM-27).
 */
class NivelConsumoDelClienteTest {

    private static final String RUTA = "http://comportamiento.test/api/comportamiento/clientes/42/indicadores"
            + "?identificador=mp-user-1";
    private static final List<ClienteOrigen> UN_IDENTIFICADOR = List.of(
            new ClienteOrigen("mp-user-1", 42L, OffsetDateTime.now()));

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://comportamiento.test");
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
    private final NivelConsumoDelCliente nivelConsumo = new NivelConsumoDelCliente(builder.build());

    @Test
    void devuelveElNivelQueCalculaComportamiento() {
        servidor.expect(requestTo(RUTA))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"frecuencia":12,"valorAcumulado":{"valor":5230.00,"compras":12,"sinDatos":false},
                        "nivelConsumo":{"nivel":"ALTO","valorAcumulado":5230.00,"sinNivel":false}}
                        """, MediaType.APPLICATION_JSON));

        NivelConsumoResponse respuesta = nivelConsumo.buscar(42L, UN_IDENTIFICADOR);

        assertThat(respuesta.nivel()).isEqualTo("ALTO");
        assertThat(respuesta.valorAcumulado()).isEqualByComparingTo("5230.00");
        assertThat(respuesta.sinDatos()).isFalse();
    }

    @Test
    void unClienteSinComprasVigentesQuedaSinNivel() {
        servidor.expect(requestTo(RUTA))
                .andRespond(withSuccess("""
                        {"nivelConsumo":{"nivel":null,"valorAcumulado":0,"sinNivel":true}}
                        """, MediaType.APPLICATION_JSON));

        NivelConsumoResponse respuesta = nivelConsumo.buscar(42L, UN_IDENTIFICADOR);

        assertThat(respuesta.sinDatos()).isTrue();
        assertThat(respuesta.nivel()).isNull();
    }

    @Test
    void sinIdentificadoresDeOrigenNoConsultaYQuedaSinNivel() {
        NivelConsumoResponse respuesta = nivelConsumo.buscar(42L, List.of());

        assertThat(respuesta.sinDatos()).isTrue();
        servidor.verify();
    }

    @Test
    void siComportamientoNoRespondeLaFichaSigueCargandoSinEseBloque() {
        servidor.expect(requestTo(RUTA)).andRespond(withServerError());

        NivelConsumoResponse respuesta = nivelConsumo.buscar(42L, UN_IDENTIFICADOR);

        assertThat(respuesta.sinDatos()).isTrue();
    }
}
